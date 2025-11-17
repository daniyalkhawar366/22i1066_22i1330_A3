<?php
// Start output buffering to prevent any unwanted output
ob_start();

// Suppress errors in output
error_reporting(E_ALL);
ini_set('display_errors', 0);

require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/../middleware/auth.php';

// Clear any previous output
ob_clean();

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

// For upload action, verify token
function getCurrentUserId() {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? '';

    if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        return null;
    }

    $token = $matches[1];
    $tokenParts = explode('.', $token);
    if (count($tokenParts) !== 3) {
        return null;
    }

    list($base64UrlHeader, $base64UrlPayload, $base64UrlSignature) = $tokenParts;

    // Verify signature
    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, JWT_SECRET, true);
    $base64UrlSignatureCheck = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));

    if ($base64UrlSignature !== $base64UrlSignatureCheck) {
        return null;
    }

    // Decode payload
    $payload = json_decode(base64_decode(str_replace(['-', '_'], ['+', '/'], $base64UrlPayload)), true);

    // Check expiration
    if (!isset($payload['exp']) || $payload['exp'] < time()) {
        return null;
    }

    return $payload['user_id'] ?? null;
}

if ($method === 'GET' && $action === 'getActive') {
    // Get all active stories (not expired)
    $now = time() * 1000; // milliseconds
    
    try {
        $database = new Database();
        $db = $database->getConnection();
        
        $stmt = $db->prepare("
            SELECT s.*, u.username, u.profile_pic_url
            FROM stories s
            JOIN users u ON s.user_id = u.id
            WHERE s.expires_at > ?
            ORDER BY s.user_id, s.uploaded_at DESC
        ");
        
        $stmt->bind_param('i', $now);
        $stmt->execute();
        $result = $stmt->get_result();
        
        $stories = [];
        while ($row = $result->fetch_assoc()) {
            $stories[] = [
                'storyId' => $row['id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'imageUrl' => $row['image_url'],
                'uploadedAt' => (int)$row['uploaded_at'],
                'expiresAt' => (int)$row['expires_at'],
                'closeFriendsOnly' => (bool)$row['close_friends_only']
            ];
        }
        
        echo json_encode(['success' => true, 'stories' => $stories]);
        $stmt->close();
    } catch (Exception $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}


if ($method === 'POST' && $action === 'upload') {
    // Upload a new story
    $currentUserId = getCurrentUserId();
    
    if (!$currentUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }
    
    $input = json_decode(file_get_contents('php://input'), true);
    $imageUrl = $input['imageUrl'] ?? '';
    $closeFriendsOnly = $input['closeFriendsOnly'] ?? false;
    
    if (empty($imageUrl)) {
        echo json_encode(['success' => false, 'error' => 'Image URL required']);
        exit;
    }
    
    try {
        $database = new Database();
        $db = $database->getConnection();
        
        $storyId = 'story_' . uniqid();
        $now = time() * 1000;
        $expires = $now + (24 * 60 * 60 * 1000); // 24 hours
        $closeFriendsInt = $closeFriendsOnly ? 1 : 0;
        
        $stmt = $db->prepare("
            INSERT INTO stories (id, user_id, image_url, uploaded_at, expires_at, close_friends_only) 
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        
        $stmt->bind_param('sssiii', $storyId, $currentUserId, $imageUrl, $now, $expires, $closeFriendsInt);
        $stmt->execute();
        
        echo json_encode([
            'success' => true, 
            'storyId' => $storyId,
            'uploadedAt' => $now,
            'expiresAt' => $expires
        ]);
        
        $stmt->close();
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

if ($method === 'GET' && $action === 'getUserStories') {
    // Get stories for a specific user
    $userId = $_GET['userId'] ?? '';

    if (empty($userId)) {
        echo json_encode(['success' => false, 'error' => 'User ID required']);
        exit;
    }

    $now = time() * 1000;

    try {
        $database = new Database();
        $db = $database->getConnection();

        $stmt = $db->prepare("
            SELECT s.*, u.username, u.profile_pic_url
            FROM stories s
            JOIN users u ON s.user_id = u.id
            WHERE s.user_id = ? AND s.expires_at > ?
            ORDER BY s.uploaded_at DESC
        ");

        $stmt->bind_param('si', $userId, $now);
        $stmt->execute();
        $result = $stmt->get_result();

        $stories = [];
        while ($row = $result->fetch_assoc()) {
            $stories[] = [
                'storyId' => $row['id'],
                'userId' => $row['user_id'],
                'username' => $row['username'],
                'profilePicUrl' => $row['profile_pic_url'] ?? '',
                'imageUrl' => $row['image_url'],
                'uploadedAt' => (int)$row['uploaded_at'],
                'expiresAt' => (int)$row['expires_at'],
                'closeFriendsOnly' => (bool)$row['close_friends_only']
            ];
        }

        echo json_encode(['success' => true, 'stories' => $stories]);
        $stmt->close();
    } catch (Exception $e) {
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

if ($method === 'POST' && $action === 'delete') {
    // Delete a story
    $currentUserId = getCurrentUserId();
    
    if (!$currentUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }
    
    $input = json_decode(file_get_contents('php://input'), true);
    $storyId = $input['storyId'] ?? '';
    
    if (empty($storyId)) {
        echo json_encode(['success' => false, 'error' => 'Story ID required']);
        exit;
    }
    
    try {
        $database = new Database();
        $db = $database->getConnection();
        
        // First verify the story belongs to the current user
        $stmt = $db->prepare("SELECT user_id, image_url FROM stories WHERE id = ?");
        $stmt->bind_param('s', $storyId);
        $stmt->execute();
        $result = $stmt->get_result();
        $story = $result->fetch_assoc();
        $stmt->close();
        
        if (!$story) {
            echo json_encode(['success' => false, 'error' => 'Story not found']);
            exit;
        }
        
        if ($story['user_id'] !== $currentUserId) {
            http_response_code(403);
            echo json_encode(['success' => false, 'error' => 'Not authorized to delete this story']);
            exit;
        }
        
        // Delete the story from database
        $stmt = $db->prepare("DELETE FROM stories WHERE id = ?");
        $stmt->bind_param('s', $storyId);
        $stmt->execute();
        $stmt->close();
        
        // Optional: Delete the image file from uploads folder
        $imageUrl = $story['image_url'];
        if (strpos($imageUrl, '/backend/uploads/') !== false) {
            $filename = basename($imageUrl);
            $filepath = __DIR__ . '/../uploads/' . $filename;
            if (file_exists($filepath)) {
                @unlink($filepath);
            }
        }
        
        echo json_encode(['success' => true, 'message' => 'Story deleted successfully']);
    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
    }
    exit;
}

echo json_encode(['success' => false, 'error' => 'Invalid request']);
?>

