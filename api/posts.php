<?php
// Start output buffering to catch any errors
ob_start();

// Turn off error display (errors should go to log only)
ini_set('display_errors', 0);
error_reporting(E_ALL);

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    ob_end_clean();
    http_response_code(200);
    exit();
}

// Error handling to always return JSON
try {
    require_once 'config.php';
    require_once 'middleware/auth.php';

    $action = $_GET['action'] ?? '';

    error_log("Posts.php - Action: $action, Method: " . $_SERVER['REQUEST_METHOD']);

// Helper function to get user info
function getUserInfo($db, $userId) {
    $stmt = $db->prepare("SELECT username, profile_pic_url FROM users WHERE id = ?");
    $stmt->bind_param("s", $userId);
    $stmt->execute();
    $result = $stmt->get_result();
    return $result->fetch_assoc();
}

switch ($action) {
    case 'create':
        try {
            // Verify token
            $userId = verifyToken();

            if (!$userId) {
                error_log("Token verification failed - no userId returned");
                echo json_encode(['success' => false, 'error' => 'Unauthorized - Invalid token']);
                exit;
            }

            error_log("Creating post for user: $userId");

            $input = file_get_contents('php://input');
            error_log("Raw input: $input");

            $data = json_decode($input, true);

            if ($data === null) {
                error_log("Failed to parse JSON input");
                echo json_encode(['success' => false, 'error' => 'Invalid JSON']);
                exit;
            }

            $postId = $data['postId'] ?? '';
            $caption = $data['caption'] ?? '';
            $imageUrls = $data['imageUrls'] ?? [];
            $timestamp = $data['timestamp'] ?? (time() * 1000);

            error_log("Post data - ID: $postId, Caption: $caption, Images: " . count($imageUrls));

            if (empty($postId) || empty($imageUrls)) {
                error_log("Missing required fields - postId: $postId, imageUrls count: " . count($imageUrls));
                echo json_encode(['success' => false, 'error' => 'Post ID and images required']);
                exit;
            }

            // Insert post
            $stmt = $db->prepare("INSERT INTO posts (id, user_id, caption, timestamp) VALUES (?, ?, ?, ?)");

            if (!$stmt) {
                error_log("Failed to prepare statement: " . $db->error);
                echo json_encode(['success' => false, 'error' => 'Database error']);
                exit;
            }

            $stmt->bind_param("sssi", $postId, $userId, $caption, $timestamp);

            if ($stmt->execute()) {
                error_log("Post inserted successfully");

                // Insert images
                $imageStmt = $db->prepare("INSERT INTO post_images (post_id, image_url, image_order) VALUES (?, ?, ?)");
                foreach ($imageUrls as $index => $url) {
                    $imageStmt->bind_param("ssi", $postId, $url, $index);
                    $imageStmt->execute();
                    error_log("Image $index inserted: $url");
                }

                // Update user's post count
                $updateStmt = $db->prepare("UPDATE users SET posts_count = posts_count + 1 WHERE id = ?");
                $updateStmt->bind_param("s", $userId);
                $updateStmt->execute();

                error_log("Post created successfully - ID: $postId");

                // Clear any error output that may have accumulated
                ob_clean();

                echo json_encode([
                    'success' => true,
                    'postId' => $postId,
                    'timestamp' => $timestamp
                ]);
            } else {
                error_log("Failed to insert post: " . $stmt->error);
                ob_clean();
                echo json_encode(['success' => false, 'error' => 'Failed to create post: ' . $stmt->error]);
            }
        } catch (Exception $e) {
            error_log("Exception in create post: " . $e->getMessage());
            ob_clean();
            echo json_encode(['success' => false, 'error' => 'Unauthorized: ' . $e->getMessage()]);
        }
        break;

    case 'getFeed':
        // Verify token
        $userId = verifyToken();

        if (!$userId) {
            echo json_encode(['success' => false, 'error' => 'Unauthorized']);
            exit;
        }

        $limit = isset($_GET['limit']) ? intval($_GET['limit']) : 50;
        $beforeTimestamp = isset($_GET['before_timestamp']) ? intval($_GET['before_timestamp']) : PHP_INT_MAX;

        // Get posts with user info
        $stmt = $db->prepare("
            SELECT p.id, p.user_id, p.caption, p.likes_count, p.comments_count, p.timestamp,
                   u.username, u.profile_pic_url
            FROM posts p
            JOIN users u ON p.user_id = u.id
            WHERE p.timestamp < ?
            ORDER BY p.timestamp DESC
            LIMIT ?
        ");
        $stmt->bind_param("ii", $beforeTimestamp, $limit);
        $stmt->execute();
        $result = $stmt->get_result();

        $posts = [];
        while ($row = $result->fetch_assoc()) {
            $postId = $row['id'];

            // Get images for this post
            $imgStmt = $db->prepare("SELECT image_url FROM post_images WHERE post_id = ? ORDER BY image_order");
            $imgStmt->bind_param("s", $postId);
            $imgStmt->execute();
            $imgResult = $imgStmt->get_result();

            $imageUrls = [];
            while ($imgRow = $imgResult->fetch_assoc()) {
                $imageUrls[] = $imgRow['image_url'];
            }

            // Check if current user liked this post
            $likeStmt = $db->prepare("SELECT id FROM post_likes WHERE post_id = ? AND user_id = ?");
            $likeStmt->bind_param("ss", $postId, $userId);
            $likeStmt->execute();
            $isLiked = $likeStmt->get_result()->num_rows > 0;

            $posts[] = [
                'id' => $row['id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'caption' => $row['caption'] ?? '',
                'imageUrls' => $imageUrls,
                'likesCount' => intval($row['likes_count']),
                'commentsCount' => intval($row['comments_count']),
                'timestamp' => intval($row['timestamp']),
                'isLikedByCurrentUser' => $isLiked
            ];
        }

        echo json_encode(['success' => true, 'posts' => $posts]);
        break;

    case 'getUserPosts':
        // Verify token
        $currentUserId = verifyToken();

        if (!$currentUserId) {
            echo json_encode(['success' => false, 'error' => 'Unauthorized']);
            exit;
        }

        $targetUserId = $_GET['userId'] ?? '';
        if (empty($targetUserId)) {
            echo json_encode(['success' => false, 'error' => 'User ID required']);
            exit;
        }

        // Get user's posts
        $stmt = $db->prepare("
            SELECT p.id, p.user_id, p.caption, p.likes_count, p.comments_count, p.timestamp,
                   u.username, u.profile_pic_url
            FROM posts p
            JOIN users u ON p.user_id = u.id
            WHERE p.user_id = ?
            ORDER BY p.timestamp DESC
        ");
        $stmt->bind_param("s", $targetUserId);
        $stmt->execute();
        $result = $stmt->get_result();

        $posts = [];
        while ($row = $result->fetch_assoc()) {
            $postId = $row['id'];

            // Get images
            $imgStmt = $db->prepare("SELECT image_url FROM post_images WHERE post_id = ? ORDER BY image_order");
            $imgStmt->bind_param("s", $postId);
            $imgStmt->execute();
            $imgResult = $imgStmt->get_result();

            $imageUrls = [];
            while ($imgRow = $imgResult->fetch_assoc()) {
                $imageUrls[] = $imgRow['image_url'];
            }

            // Check if liked by current user
            $likeStmt = $db->prepare("SELECT id FROM post_likes WHERE post_id = ? AND user_id = ?");
            $likeStmt->bind_param("ss", $postId, $currentUserId);
            $likeStmt->execute();
            $isLiked = $likeStmt->get_result()->num_rows > 0;

            $posts[] = [
                'id' => $row['id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'caption' => $row['caption'] ?? '',
                'imageUrls' => $imageUrls,
                'likesCount' => intval($row['likes_count']),
                'commentsCount' => intval($row['comments_count']),
                'timestamp' => intval($row['timestamp']),
                'isLikedByCurrentUser' => $isLiked
            ];
        }

        echo json_encode(['success' => true, 'posts' => $posts]);
        break;

    case 'toggleLike':
        // Verify token
        $userId = verifyToken();

        if (!$userId) {
            echo json_encode(['success' => false, 'error' => 'Unauthorized']);
            exit;
        }

        $data = json_decode(file_get_contents('php://input'), true);
        $postId = $data['postId'] ?? '';

        if (empty($postId)) {
            echo json_encode(['success' => false, 'error' => 'Post ID required']);
            exit;
        }

        // Check if already liked
        $checkStmt = $db->prepare("SELECT id FROM post_likes WHERE post_id = ? AND user_id = ?");
        $checkStmt->bind_param("ss", $postId, $userId);
        $checkStmt->execute();
        $exists = $checkStmt->get_result()->num_rows > 0;

        if ($exists) {
            // Unlike
            $deleteStmt = $db->prepare("DELETE FROM post_likes WHERE post_id = ? AND user_id = ?");
            $deleteStmt->bind_param("ss", $postId, $userId);
            $deleteStmt->execute();

            $updateStmt = $db->prepare("UPDATE posts SET likes_count = GREATEST(likes_count - 1, 0) WHERE id = ?");
            $updateStmt->bind_param("s", $postId);
            $updateStmt->execute();

            $isLiked = false;
        } else {
            // Like
            $insertStmt = $db->prepare("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)");
            $insertStmt->bind_param("ss", $postId, $userId);
            $insertStmt->execute();

            $updateStmt = $db->prepare("UPDATE posts SET likes_count = likes_count + 1 WHERE id = ?");
            $updateStmt->bind_param("s", $postId);
            $updateStmt->execute();

            $isLiked = true;
        }

        // Get updated like count
        $countStmt = $db->prepare("SELECT likes_count FROM posts WHERE id = ?");
        $countStmt->bind_param("s", $postId);
        $countStmt->execute();
        $countResult = $countStmt->get_result();
        $likesCount = $countResult->fetch_assoc()['likes_count'];

        echo json_encode([
            'success' => true,
            'isLiked' => $isLiked,
            'likesCount' => intval($likesCount)
        ]);
        break;

    case 'addComment':
        // Verify token
        $userId = verifyToken();

        if (!$userId) {
            echo json_encode(['success' => false, 'error' => 'Unauthorized']);
            exit;
        }

        $data = json_decode(file_get_contents('php://input'), true);
        $postId = $data['postId'] ?? '';
        $commentId = $data['commentId'] ?? '';
        $text = $data['text'] ?? '';
        $timestamp = $data['timestamp'] ?? (time() * 1000);

        if (empty($postId) || empty($commentId) || empty($text)) {
            echo json_encode(['success' => false, 'error' => 'Post ID, comment ID, and text required']);
            exit;
        }

        // Insert comment
        $stmt = $db->prepare("INSERT INTO post_comments (id, post_id, user_id, text, timestamp) VALUES (?, ?, ?, ?, ?)");
        $stmt->bind_param("ssssi", $commentId, $postId, $userId, $text, $timestamp);

        if ($stmt->execute()) {
            // Update comment count
            $updateStmt = $db->prepare("UPDATE posts SET comments_count = comments_count + 1 WHERE id = ?");
            $updateStmt->bind_param("s", $postId);
            $updateStmt->execute();

            // Get user info
            $userInfo = getUserInfo($db, $userId);

            echo json_encode([
                'success' => true,
                'comment' => [
                    'id' => $commentId,
                    'postId' => $postId,
                    'userId' => $userId,
                    'username' => $userInfo['username'],
                    'profilePicUrl' => $userInfo['profile_pic_url'] ?? '',
                    'text' => $text,
                    'timestamp' => $timestamp
                ]
            ]);
        } else {
            echo json_encode(['success' => false, 'error' => 'Failed to add comment']);
        }
        break;

    case 'getComments':
        // Verify token
        $headers = getallheaders();
        $token = $headers['Authorization'] ?? '';
        $userId = verifyToken($token);

        if (!$userId) {
            echo json_encode(['success' => false, 'error' => 'Unauthorized']);
            exit;
        }

        $postId = $_GET['postId'] ?? '';
        if (empty($postId)) {
            echo json_encode(['success' => false, 'error' => 'Post ID required']);
            exit;
        }

        $stmt = $conn->prepare("
            SELECT c.id, c.post_id, c.user_id, c.text, c.timestamp,
                   u.username, u.profile_pic_url
            FROM post_comments c
            JOIN users u ON c.user_id = u.id
            WHERE c.post_id = ?
            ORDER BY c.timestamp ASC
        ");
        $stmt->bind_param("s", $postId);
        $stmt->execute();
        $result = $stmt->get_result();

        $comments = [];
        while ($row = $result->fetch_assoc()) {
            $comments[] = [
                'id' => $row['id'],
                'postId' => $row['post_id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'text' => $row['text'],
                'timestamp' => intval($row['timestamp'])
            ];
        }

        echo json_encode(['success' => true, 'comments' => $comments]);
        break;

    case 'deletePost':
        // Verify token
        $headers = getallheaders();
        $token = $headers['Authorization'] ?? '';
        $userId = verifyToken($token);

        if (!$userId) {
            echo json_encode(['success' => false, 'error' => 'Unauthorized']);
            exit;
        }

        $data = json_decode(file_get_contents('php://input'), true);
        $postId = $data['postId'] ?? '';

        if (empty($postId)) {
            echo json_encode(['success' => false, 'error' => 'Post ID required']);
            exit;
        }

        // Verify ownership
        $checkStmt = $conn->prepare("SELECT user_id FROM posts WHERE id = ?");
        $checkStmt->bind_param("s", $postId);
        $checkStmt->execute();
        $result = $checkStmt->get_result();

        if ($result->num_rows === 0) {
            echo json_encode(['success' => false, 'error' => 'Post not found']);
            exit;
        }

        $post = $result->fetch_assoc();
        if ($post['user_id'] !== $userId) {
            echo json_encode(['success' => false, 'error' => 'Not authorized to delete this post']);
            exit;
        }

        // Delete post (cascade will handle images, likes, comments)
        $deleteStmt = $conn->prepare("DELETE FROM posts WHERE id = ?");
        $deleteStmt->bind_param("s", $postId);

        if ($deleteStmt->execute()) {
            // Update user's post count
            $updateStmt = $conn->prepare("UPDATE users SET posts_count = GREATEST(posts_count - 1, 0) WHERE id = ?");
            $updateStmt->bind_param("s", $userId);
            $updateStmt->execute();

            echo json_encode(['success' => true, 'message' => 'Post deleted']);
        } else {
            echo json_encode(['success' => false, 'error' => 'Failed to delete post']);
        }
        break;

    default:
        echo json_encode(['success' => false, 'error' => 'Invalid action']);
        break;
}

} catch (Exception $e) {
    error_log("CRITICAL ERROR in posts.php: " . $e->getMessage());
    error_log("Stack trace: " . $e->getTraceAsString());

    // Clear any output buffer content (errors, warnings, etc.)
    ob_clean();

    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Server error: ' . $e->getMessage()
    ]);
}

// Flush the output buffer and send response
ob_end_flush();
?>

