<?php
require_once 'config.php';
require_once __DIR__ . '/../middleware/auth.php';
header('Content-Type: application/json');

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

// Get user profile
if ($method === 'GET' && $action === 'profile') {
    $userId = $_GET['userId'] ?? '';
    $currentUserId = $_GET['currentUserId'] ?? '';

    if (empty($userId)) {
        echo json_encode(['success' => false, 'error' => 'User ID required']);
        exit;
    }
    
    try {
        $db = getDB();

        // Get user data with all fields
        $stmt = $db->prepare("
            SELECT id, username, display_name, first_name, last_name,
                   profile_pic_url, bio, title, threads_username, website, email,
                   followers_count, following_count, posts_count
            FROM users
            WHERE id = :userId
        ");
        $stmt->bindParam(':userId', $userId);
        $stmt->execute();
        
        $user = $stmt->fetch(PDO::FETCH_ASSOC);
        
        if (!$user) {
            echo json_encode(['success' => false, 'error' => 'User not found']);
            exit;
        }

        // Check if current user is following this user
        $isFollowing = false;
        if (!empty($currentUserId) && $currentUserId !== $userId) {
            $followStmt = $db->prepare("
                SELECT COUNT(*) as count
                FROM follows
                WHERE follower_id = :currentUserId AND following_id = :userId
            ");
            $followStmt->bindParam(':currentUserId', $currentUserId);
            $followStmt->bindParam(':userId', $userId);
            $followStmt->execute();
            $followResult = $followStmt->fetch(PDO::FETCH_ASSOC);
            $isFollowing = ($followResult['count'] > 0);
        }

        echo json_encode([
            'success' => true,
            'user' => [
                'id' => $user['id'],
                'username' => $user['username'] ?? '',
                'displayName' => $user['display_name'] ?? '',
                'firstName' => $user['first_name'] ?? '',
                'lastName' => $user['last_name'] ?? '',
                'profilePicUrl' => $user['profile_pic_url'] ?? '',
                'bio' => $user['bio'] ?? '',
                'title' => $user['title'] ?? '',
                'threadsUsername' => $user['threads_username'] ?? '',
                'website' => $user['website'] ?? '',
                'email' => $user['email'] ?? '',
                'followersCount' => (int)$user['followers_count'],
                'followingCount' => (int)$user['following_count'],
                'postsCount' => (int)$user['posts_count'],
                'isFollowing' => $isFollowing
            ]
        ]);
    } catch (PDOException $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

// Check follow status
if ($method === 'GET' && $action === 'checkFollow') {
    $authUserId = authenticateRequest();
    if (!$authUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }

    $targetUserId = $_GET['targetUserId'] ?? '';
    if (empty($targetUserId)) {
        echo json_encode(['success' => false, 'error' => 'Target user ID required']);
        exit;
    }

    try {
        $db = getDB();
        $stmt = $db->prepare("
            SELECT COUNT(*) as count
            FROM follows
            WHERE follower_id = :follower AND following_id = :following
        ");
        $stmt->bindParam(':follower', $authUserId);
        $stmt->bindParam(':following', $targetUserId);
        $stmt->execute();
        $result = $stmt->fetch(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'isFollowing' => ($result['count'] > 0)
        ]);
    } catch (PDOException $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

// Follow user
if ($method === 'POST' && $action === 'follow') {
    $authUserId = authenticateRequest();
    if (!$authUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }

    $input = json_decode(file_get_contents('php://input'), true);
    $targetUserId = $input['targetUserId'] ?? '';

    if (empty($targetUserId)) {
        echo json_encode(['success' => false, 'error' => 'Target user ID required']);
        exit;
    }

    if ($authUserId === $targetUserId) {
        echo json_encode(['success' => false, 'error' => 'Cannot follow yourself']);
        exit;
    }

    try {
        $db = getDB();

        // Check if already following
        $checkStmt = $db->prepare("
            SELECT COUNT(*) as count
            FROM follows
            WHERE follower_id = :follower AND following_id = :following
        ");
        $checkStmt->bindParam(':follower', $authUserId);
        $checkStmt->bindParam(':following', $targetUserId);
        $checkStmt->execute();
        $exists = $checkStmt->fetch(PDO::FETCH_ASSOC);

        if ($exists['count'] > 0) {
            echo json_encode(['success' => false, 'error' => 'Already following']);
            exit;
        }

        // Insert follow relationship
        $insertStmt = $db->prepare("
            INSERT INTO follows (follower_id, following_id)
            VALUES (:follower, :following)
        ");
        $insertStmt->bindParam(':follower', $authUserId);
        $insertStmt->bindParam(':following', $targetUserId);
        $insertStmt->execute();

        // Update follower count for target user
        $db->prepare("UPDATE users SET followers_count = followers_count + 1 WHERE id = :id")
            ->execute([':id' => $targetUserId]);

        // Update following count for auth user
        $db->prepare("UPDATE users SET following_count = following_count + 1 WHERE id = :id")
            ->execute([':id' => $authUserId]);

        echo json_encode(['success' => true, 'message' => 'Followed successfully']);
    } catch (PDOException $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

// Unfollow user
if ($method === 'POST' && $action === 'unfollow') {
    $authUserId = authenticateRequest();
    if (!$authUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }

    $input = json_decode(file_get_contents('php://input'), true);
    $targetUserId = $input['targetUserId'] ?? '';

    if (empty($targetUserId)) {
        echo json_encode(['success' => false, 'error' => 'Target user ID required']);
        exit;
    }

    try {
        $db = getDB();

        // Delete follow relationship
        $deleteStmt = $db->prepare("
            DELETE FROM follows
            WHERE follower_id = :follower AND following_id = :following
        ");
        $deleteStmt->bindParam(':follower', $authUserId);
        $deleteStmt->bindParam(':following', $targetUserId);
        $deleteStmt->execute();

        if ($deleteStmt->rowCount() > 0) {
            // Update follower count for target user
            $db->prepare("UPDATE users SET followers_count = GREATEST(0, followers_count - 1) WHERE id = :id")
                ->execute([':id' => $targetUserId]);

            // Update following count for auth user
            $db->prepare("UPDATE users SET following_count = GREATEST(0, following_count - 1) WHERE id = :id")
                ->execute([':id' => $authUserId]);
        }

        echo json_encode(['success' => true, 'message' => 'Unfollowed successfully']);
    } catch (PDOException $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

// Update user profile
if ($method === 'POST' && $action === 'updateProfile') {
    $authUserId = authenticateRequest();
    if (!$authUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }

    $input = json_decode(file_get_contents('php://input'), true);

    $updateFields = [];
    $params = [':userId' => $authUserId];

    if (isset($input['displayName'])) {
        $updateFields[] = "display_name = :displayName";
        $params[':displayName'] = $input['displayName'];
    }
    if (isset($input['firstName'])) {
        $updateFields[] = "first_name = :firstName";
        $params[':firstName'] = $input['firstName'];
    }
    if (isset($input['lastName'])) {
        $updateFields[] = "last_name = :lastName";
        $params[':lastName'] = $input['lastName'];
    }
    if (isset($input['bio'])) {
        $updateFields[] = "bio = :bio";
        $params[':bio'] = $input['bio'];
    }
    if (isset($input['title'])) {
        $updateFields[] = "title = :title";
        $params[':title'] = $input['title'];
    }
    if (isset($input['threadsUsername'])) {
        $updateFields[] = "threads_username = :threadsUsername";
        $params[':threadsUsername'] = $input['threadsUsername'];
    }
    if (isset($input['website'])) {
        $updateFields[] = "website = :website";
        $params[':website'] = $input['website'];
    }
    if (isset($input['profilePicUrl'])) {
        $updateFields[] = "profile_pic_url = :profilePicUrl";
        $params[':profilePicUrl'] = $input['profilePicUrl'];
    }

    if (empty($updateFields)) {
        echo json_encode(['success' => false, 'error' => 'No fields to update']);
        exit;
    }

    try {
        $db = getDB();
        $sql = "UPDATE users SET " . implode(', ', $updateFields) . " WHERE id = :userId";
        $stmt = $db->prepare($sql);
        $stmt->execute($params);

        echo json_encode(['success' => true, 'message' => 'Profile updated successfully']);
    } catch (PDOException $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

echo json_encode(['success' => false, 'error' => 'Invalid request']);
?>
