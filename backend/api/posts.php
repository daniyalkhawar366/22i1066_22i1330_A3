<?php
// Prevent any output before JSON
ob_start();

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/jwt_helper.php';

// Clear any accidental output
ob_end_clean();

// Initialize database
try {
    $db = getDB();
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Database connection failed']);
    exit();
}

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

error_log("=== Posts.php Request ===");
error_log("Method: $method");
error_log("Action: $action");
error_log("Headers: " . print_r(getallheaders(), true));

// Handle getUserPosts without authentication requirement (public endpoint)
if ($method === 'GET' && $action === 'getUserPosts') {
    $userId = $_GET['userId'] ?? '';

    if (empty($userId)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'userId required']);
        exit();
    }

    // Get current user ID from token if available (for isLiked status)
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? '';
    $token = str_replace('Bearer ', '', $authHeader);
    $userData = verifyJWT($token);
    $currentUserId = $userData['user_id'] ?? '';

    try {
        $stmt = $db->prepare("
            SELECT p.id, p.user_id, p.caption, p.likes_count, p.comments_count, p.timestamp,
                   u.username, u.profile_pic_url
            FROM posts p
            JOIN users u ON p.user_id = u.id
            WHERE p.user_id = ?
            ORDER BY p.timestamp DESC
        ");
        $stmt->execute([$userId]);
        $result = $stmt->fetchAll(PDO::FETCH_ASSOC);

        $posts = [];
        foreach ($result as $row) {
            $postId = $row['id'];

            // Get images
            $imgStmt = $db->prepare("SELECT image_url FROM post_images WHERE post_id = ? ORDER BY image_order");
            $imgStmt->execute([$postId]);
            $imageUrls = $imgStmt->fetchAll(PDO::FETCH_COLUMN);

            // Check if current user liked this post
            $isLiked = false;
            if ($currentUserId) {
                $likeStmt = $db->prepare("SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND user_id = ?");
                $likeStmt->execute([$postId, $currentUserId]);
                $isLiked = $likeStmt->fetchColumn() > 0;
            }

            $posts[] = [
                'id' => $row['id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'caption' => $row['caption'],
                'imageUrls' => $imageUrls,
                'likesCount' => intval($row['likes_count']),
                'commentsCount' => intval($row['comments_count']),
                'timestamp' => intval($row['timestamp']),
                'isLikedByCurrentUser' => $isLiked
            ];
        }

        http_response_code(200);
        echo json_encode(['success' => true, 'posts' => $posts]);
        exit();

    } catch (Exception $e) {
        error_log("getUserPosts error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to load posts']);
        exit();
    }
}

// Get and verify JWT token for other endpoints
$headers = getallheaders();
$authHeader = $headers['Authorization'] ?? '';
$token = str_replace('Bearer ', '', $authHeader);
$userData = verifyJWT($token);

if (!$userData) {
    http_response_code(401);
    echo json_encode(['success' => false, 'error' => 'Unauthorized']);
    exit();
}

$currentUserId = $userData['user_id'];

// CREATE POST
if ($method === 'POST' && $action === 'create') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);

    if (!isset($data['postId']) || !isset($data['imageUrls']) || empty($data['imageUrls'])) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'postId and imageUrls required']);
        exit();
    }

    $postId = $data['postId'];
    $caption = $data['caption'] ?? '';
    $imageUrls = $data['imageUrls'];
    $timestamp = $data['timestamp'] ?? (time() * 1000);

    try {
        // Insert post
        $stmt = $db->prepare("INSERT INTO posts (id, user_id, caption, timestamp) VALUES (?, ?, ?, ?)");
        $stmt->execute([$postId, $currentUserId, $caption, $timestamp]);

        // Insert images
        $imageInsertStmt = $db->prepare("INSERT INTO post_images (post_id, image_url, image_order) VALUES (?, ?, ?)");
        foreach ($imageUrls as $index => $url) {
            $imageInsertStmt->execute([$postId, $url, $index]);
        }

        // Update user's posts_count
        $updateStmt = $db->prepare("UPDATE users SET posts_count = posts_count + 1 WHERE id = ?");
        $updateStmt->execute([$currentUserId]);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'postId' => $postId,
            'timestamp' => $timestamp,
            'error' => null
        ]);
        exit();

    } catch (Exception $e) {
        error_log("Exception during post creation: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
        exit();
    }
}

// GET FEED
elseif ($method === 'GET' && $action === 'getFeed') {
    error_log("=== getFeed endpoint reached ===");
    error_log("Method: $method, Action: $action");
    error_log("Current User ID: $currentUserId");

    try {
        $limit = intval($_GET['limit'] ?? 50);
        $beforeTimestamp = intval($_GET['before_timestamp'] ?? PHP_INT_MAX);

        error_log("getFeed called - limit: $limit, beforeTimestamp: $beforeTimestamp, currentUserId: $currentUserId");

        $stmt = $db->prepare("
            SELECT p.id, p.user_id, p.caption, p.likes_count, p.comments_count, p.timestamp,
                   u.username, u.profile_pic_url
            FROM posts p
            JOIN users u ON p.user_id = u.id
            WHERE p.timestamp < ?
            ORDER BY p.timestamp DESC
            LIMIT ?
        ");
        $stmt->execute([$beforeTimestamp, $limit]);
        $result = $stmt->fetchAll(PDO::FETCH_ASSOC);

        $posts = [];
        foreach ($result as $row) {
            $postId = $row['id'];

            // Get images for this post
            $imgStmt = $db->prepare("SELECT image_url FROM post_images WHERE post_id = ? ORDER BY image_order");
            $imgStmt->execute([$postId]);
            $imageUrls = $imgStmt->fetchAll(PDO::FETCH_COLUMN);

            // Check if current user liked this post
            $likeStmt = $db->prepare("SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND user_id = ?");
            $likeStmt->execute([$postId, $currentUserId]);
            $isLiked = $likeStmt->fetchColumn() > 0;

            // Get preview comments (first 2 comments) for this post
            $commentStmt = $db->prepare("
                SELECT c.id, c.user_id, c.text, c.timestamp, u.username, u.profile_pic_url
                FROM post_comments c
                JOIN users u ON c.user_id = u.id
                WHERE c.post_id = ?
                ORDER BY c.timestamp ASC
                LIMIT 2
            ");
            $commentStmt->execute([$postId]);
            $commentResult = $commentStmt->fetchAll(PDO::FETCH_ASSOC);

            $previewComments = [];
            foreach ($commentResult as $commentRow) {
                $previewComments[] = [
                    'id' => $commentRow['id'],
                    'userId' => $commentRow['user_id'],
                    'username' => $commentRow['username'],
                    'profilePicUrl' => $commentRow['profile_pic_url'] ?? '',
                    'text' => $commentRow['text'],
                    'timestamp' => intval($commentRow['timestamp'])
                ];
            }

            $posts[] = [
                'id' => $row['id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'caption' => $row['caption'],
                'imageUrls' => $imageUrls,
                'likesCount' => intval($row['likes_count']),
                'commentsCount' => intval($row['comments_count']),
                'timestamp' => intval($row['timestamp']),
                'isLikedByCurrentUser' => $isLiked,
                'previewComments' => $previewComments
            ];
        }

        error_log("getFeed returning " . count($posts) . " posts");
        http_response_code(200);
        echo json_encode(['success' => true, 'posts' => $posts]);
        exit();

    } catch (Exception $e) {
        error_log("Exception in getFeed: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => $e->getMessage(), 'posts' => []]);
        exit();
    }
}

// TOGGLE LIKE
elseif ($method === 'POST' && $action === 'toggleLike') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    $postId = $data['postId'] ?? '';

    try {
        $stmt = $db->prepare("SELECT id FROM post_likes WHERE post_id = ? AND user_id = ?");
        $stmt->execute([$postId, $currentUserId]);
        $result = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($result) {
            // Unlike
            $stmt = $db->prepare("DELETE FROM post_likes WHERE post_id = ? AND user_id = ?");
            $stmt->execute([$postId, $currentUserId]);

            $stmt = $db->prepare("UPDATE posts SET likes_count = likes_count - 1 WHERE id = ?");
            $stmt->execute([$postId]);

            $isLiked = false;
        } else {
            // Like
            $stmt = $db->prepare("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)");
            $stmt->execute([$postId, $currentUserId]);

            $stmt = $db->prepare("UPDATE posts SET likes_count = likes_count + 1 WHERE id = ?");
            $stmt->execute([$postId]);

            $isLiked = true;
        }

        $stmt = $db->prepare("SELECT likes_count FROM posts WHERE id = ?");
        $stmt->execute([$postId]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'isLiked' => $isLiked,
            'likesCount' => intval($row['likes_count'])
        ]);
        exit();
    } catch (Exception $e) {
        error_log("toggleLike error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to toggle like']);
        exit();
    }
}

// ADD COMMENT
elseif ($method === 'POST' && $action === 'addComment') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);

    $commentId = $data['commentId'];
    $postId = $data['postId'];
    $text = $data['text'];
    $timestamp = $data['timestamp'];

    try {
        $stmt = $db->prepare("INSERT INTO post_comments (id, post_id, user_id, text, timestamp) VALUES (?, ?, ?, ?, ?)");
        $stmt->execute([$commentId, $postId, $currentUserId, $text, $timestamp]);

        $stmt = $db->prepare("UPDATE posts SET comments_count = comments_count + 1 WHERE id = ?");
        $stmt->execute([$postId]);

        $stmt = $db->prepare("SELECT u.username, u.profile_pic_url FROM users u WHERE u.id = ?");
        $stmt->execute([$currentUserId]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'comment' => [
                'id' => $commentId,
                'postId' => $postId,
                'userId' => $currentUserId,
                'username' => $user['username'],
                'profilePicUrl' => $user['profile_pic_url'] ?? '',
                'text' => $text,
                'timestamp' => $timestamp
            ]
        ]);
        exit();
    } catch (Exception $e) {
        error_log("addComment error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to add comment']);
        exit();
    }
}

// GET COMMENTS
elseif ($method === 'GET' && $action === 'getComments') {
    $postId = $_GET['postId'] ?? '';

    try {
        $stmt = $db->prepare("
            SELECT c.id, c.post_id, c.user_id, c.text, c.timestamp,
                   u.username, u.profile_pic_url
            FROM post_comments c
            JOIN users u ON c.user_id = u.id
            WHERE c.post_id = ?
            ORDER BY c.timestamp ASC
        ");
        $stmt->execute([$postId]);
        $result = $stmt->fetchAll(PDO::FETCH_ASSOC);

        $comments = [];
        foreach ($result as $row) {
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

        http_response_code(200);
        echo json_encode(['success' => true, 'comments' => $comments]);
        exit();
    } catch (Exception $e) {
        error_log("getComments error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to get comments']);
        exit();
    }
}

// DELETE POST
elseif ($method === 'POST' && $action === 'deletePost') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    $postId = $data['postId'] ?? '';

    try {
        // Delete the post (images will be deleted automatically due to ON DELETE CASCADE)
        $stmt = $db->prepare("DELETE FROM posts WHERE id = ? AND user_id = ?");
        $stmt->execute([$postId, $currentUserId]);

        // If post was deleted, decrement the user's posts_count
        if ($stmt->rowCount() > 0) {
            $updateStmt = $db->prepare("UPDATE users SET posts_count = GREATEST(0, posts_count - 1) WHERE id = ?");
            $updateStmt->execute([$currentUserId]);
        }

        http_response_code(200);
        echo json_encode(['success' => true]);
        exit();
    } catch (Exception $e) {
        error_log("deletePost error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to delete post']);
        exit();
    }
}

// Invalid action
else {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid action']);
    exit();
}

