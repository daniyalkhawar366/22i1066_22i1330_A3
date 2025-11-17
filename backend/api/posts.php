<?php
// Prevent any output before JSON
ob_start();

require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/auth.php';

// Clear any accidental output
ob_end_clean();

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

// Get and verify JWT token
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
        // Insert post - use timestamp (BIGINT) column
        $stmt = $db->prepare("INSERT INTO posts (id, user_id, caption, timestamp) VALUES (?, ?, ?, ?)");
        $stmt->bind_param("sssi", $postId, $currentUserId, $caption, $timestamp);

        if (!$stmt->execute()) {
            error_log("Failed to insert post: " . $stmt->error);
            http_response_code(500);
            echo json_encode(['success' => false, 'error' => 'Failed to create post: ' . $stmt->error]);
            exit();
        }

        // Insert images
        $imageInsertStmt = $db->prepare("INSERT INTO post_images (post_id, image_url, image_order) VALUES (?, ?, ?)");
        foreach ($imageUrls as $index => $url) {
            $imageInsertStmt->bind_param("ssi", $postId, $url, $index);
            if (!$imageInsertStmt->execute()) {
                error_log("Failed to insert image $index: " . $imageInsertStmt->error);
            }
        }
        $imageInsertStmt->close();

        // Update user's posts_count
        $updateStmt = $db->prepare("UPDATE users SET posts_count = posts_count + 1 WHERE id = ?");
        $updateStmt->bind_param("s", $currentUserId);
        $updateStmt->execute();
        $updateStmt->close();

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


elseif ($method === 'GET' && $action === 'getFeed') {
    try {
        $limit = intval($_GET['limit'] ?? 50);
        $beforeTimestamp = intval($_GET['before_timestamp'] ?? PHP_INT_MAX);

        error_log("getFeed called - limit: $limit, beforeTimestamp: $beforeTimestamp, currentUserId: $currentUserId");

        $stmt = $db->prepare("
            SELECT p.id, p.user_id, p.caption, p.likes_count, p.comments_count, p.timestamp,
                   u.username, u.profile_pic_url,
                   (SELECT COUNT(*) FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked
            FROM posts p
            JOIN users u ON p.user_id = u.id
            WHERE p.timestamp < ?
            ORDER BY p.timestamp DESC
            LIMIT ?
        ");
        $stmt->bind_param("sii", $currentUserId, $beforeTimestamp, $limit);
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

            // Get preview comments (first 2 comments) for this post
            $commentStmt = $db->prepare("
                SELECT c.id, c.user_id, c.text, c.timestamp, u.username, u.profile_pic_url
                FROM post_comments c
                JOIN users u ON c.user_id = u.id
                WHERE c.post_id = ?
                ORDER BY c.timestamp ASC
                LIMIT 2
            ");
            $commentStmt->bind_param("s", $postId);
            $commentStmt->execute();
            $commentResult = $commentStmt->get_result();

            $previewComments = [];
            while ($commentRow = $commentResult->fetch_assoc()) {
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
                'isLikedByCurrentUser' => $row['is_liked'] > 0,
                'previewComments' => $previewComments
            ];
        }

        error_log("getFeed returning " . count($posts) . " posts");
        echo json_encode(['success' => true, 'posts' => $posts]);
        exit();

    } catch (Exception $e) {
        error_log("Exception in getFeed: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => $e->getMessage(), 'posts' => []]);
        exit();
    }
}

elseif ($method === 'GET' && $action === 'getUserPosts') {
    $userId = $_GET['userId'] ?? '';
    
    $stmt = $db->prepare("
        SELECT p.id, p.user_id, p.caption, p.likes_count, p.comments_count, p.timestamp,
               u.username, u.profile_pic_url,
               (SELECT COUNT(*) FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked
        FROM posts p
        JOIN users u ON p.user_id = u.id
        WHERE p.user_id = ?
        ORDER BY p.timestamp DESC
    ");
    $stmt->bind_param("ss", $currentUserId, $userId);
    $stmt->execute();
    $result = $stmt->get_result();

    $posts = [];
    while ($row = $result->fetch_assoc()) {
        $postId = $row['id'];
        
        $imgStmt = $db->prepare("SELECT image_url FROM post_images WHERE post_id = ? ORDER BY image_order");
        $imgStmt->bind_param("s", $postId);
        $imgStmt->execute();
        $imgResult = $imgStmt->get_result();
        
        $imageUrls = [];
        while ($imgRow = $imgResult->fetch_assoc()) {
            $imageUrls[] = $imgRow['image_url'];
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
            'isLikedByCurrentUser' => $row['is_liked'] > 0
        ];
    }

    echo json_encode(['success' => true, 'posts' => $posts]);
}

elseif ($method === 'POST' && $action === 'toggleLike') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    $postId = $data['postId'] ?? '';

    $stmt = $db->prepare("SELECT id FROM post_likes WHERE post_id = ? AND user_id = ?");
    $stmt->bind_param("ss", $postId, $currentUserId);
    $stmt->execute();
    $result = $stmt->get_result();

    if ($result->num_rows > 0) {
        // Unlike
        $stmt = $db->prepare("DELETE FROM post_likes WHERE post_id = ? AND user_id = ?");
        $stmt->bind_param("ss", $postId, $currentUserId);
        $stmt->execute();

        $stmt = $db->prepare("UPDATE posts SET likes_count = likes_count - 1 WHERE id = ?");
        $stmt->bind_param("s", $postId);
        $stmt->execute();

        $isLiked = false;
    } else {
        // Like
        $stmt = $db->prepare("INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)");
        $stmt->bind_param("ss", $postId, $currentUserId);
        $stmt->execute();

        $stmt = $db->prepare("UPDATE posts SET likes_count = likes_count + 1 WHERE id = ?");
        $stmt->bind_param("s", $postId);
        $stmt->execute();

        $isLiked = true;
    }

    $stmt = $db->prepare("SELECT likes_count FROM posts WHERE id = ?");
    $stmt->bind_param("s", $postId);
    $stmt->execute();
    $result = $stmt->get_result();
    $row = $result->fetch_assoc();

    echo json_encode([
        'success' => true,
        'isLiked' => $isLiked,
        'likesCount' => intval($row['likes_count'])
    ]);
}

elseif ($method === 'POST' && $action === 'addComment') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);

    $commentId = $data['commentId'];
    $postId = $data['postId'];
    $text = $data['text'];
    $timestamp = $data['timestamp'];

    $stmt = $db->prepare("INSERT INTO post_comments (id, post_id, user_id, text, timestamp) VALUES (?, ?, ?, ?, ?)");
    $stmt->bind_param("ssssi", $commentId, $postId, $currentUserId, $text, $timestamp);
    $stmt->execute();

    $stmt = $db->prepare("UPDATE posts SET comments_count = comments_count + 1 WHERE id = ?");
    $stmt->bind_param("s", $postId);
    $stmt->execute();

    $stmt = $db->prepare("SELECT u.username, u.profile_pic_url FROM users u WHERE u.id = ?");
    $stmt->bind_param("s", $currentUserId);
    $stmt->execute();
    $result = $stmt->get_result();
    $user = $result->fetch_assoc();

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
}

elseif ($method === 'GET' && $action === 'getComments') {
    $postId = $_GET['postId'] ?? '';

    $stmt = $db->prepare("
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
}

elseif ($method === 'POST' && $action === 'deletePost') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    $postId = $data['postId'] ?? '';

    $stmt = $db->prepare("DELETE FROM posts WHERE id = ? AND user_id = ?");
    $stmt->bind_param("ss", $postId, $currentUserId);
    $stmt->execute();

    echo json_encode(['success' => true]);
}

else {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid action']);
}
?>
