<?php
// Prevent any output before JSON
ob_start();

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/jwt_helper.php';

// Clear any accidental output
ob_end_clean();

header('Content-Type: application/json');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

try {
    $db = getDB();
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Database connection failed']);
    exit();
}

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

// CREATE HIGHLIGHT
if ($method === 'POST' && $action === 'create') {
    try {
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

        $authUserId = $userData['user_id'];

        $input = file_get_contents('php://input');
        $data = json_decode($input, true);

        if (empty($data['title']) || empty($data['imageUrls']) || empty($data['date'])) {
            http_response_code(400);
            echo json_encode(['success' => false, 'error' => 'Title, images, and date required']);
            exit();
        }

        $highlightId = uniqid('highlight_', true);
        $imageUrlsJson = json_encode($data['imageUrls']);
        $dateTimestamp = intval($data['date']); // Unix timestamp in milliseconds
        $dateSeconds = floor($dateTimestamp / 1000); // Convert to seconds

        // Use PDO instead of mysqli
        $stmt = $db->prepare("INSERT INTO highlights (id, user_id, title, image_urls, date, created_at) VALUES (?, ?, ?, ?, FROM_UNIXTIME(?), NOW())");
        $stmt->execute([$highlightId, $authUserId, $data['title'], $imageUrlsJson, $dateSeconds]);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'highlightId' => $highlightId,
            'message' => 'Highlight created successfully'
        ]);
        exit();

    } catch (Exception $e) {
        error_log("Highlight creation error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to create highlight: ' . $e->getMessage()]);
        exit();
    }
}

// GET USER HIGHLIGHTS
elseif ($method === 'GET' && $action === 'getUserHighlights') {
    $userId = $_GET['userId'] ?? '';

    if (empty($userId)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'User ID required']);
        exit();
    }

    try {
        // Use PDO instead of mysqli
        $stmt = $db->prepare("SELECT id, user_id, title, image_urls, UNIX_TIMESTAMP(date) as date FROM highlights WHERE user_id = ? ORDER BY date DESC");
        $stmt->execute([$userId]);
        $highlights = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // Parse JSON image_urls
        foreach ($highlights as &$highlight) {
            $highlight['imageUrls'] = json_decode($highlight['image_urls'], true) ?? [];
            $highlight['date'] = intval($highlight['date']) * 1000; // Convert to milliseconds
            unset($highlight['image_urls']);
        }

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'highlights' => $highlights
        ]);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to fetch highlights: ' . $e->getMessage()]);
        exit();
    }
}

// GET SINGLE HIGHLIGHT
elseif ($method === 'GET' && $action === 'getHighlight') {
    $highlightId = $_GET['highlightId'] ?? '';

    if (empty($highlightId)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Highlight ID required']);
        exit();
    }

    try {
        $stmt = $db->prepare("SELECT id, user_id, title, image_urls, UNIX_TIMESTAMP(date) as date FROM highlights WHERE id = ?");
        $stmt->execute([$highlightId]);
        $highlight = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$highlight) {
            http_response_code(404);
            echo json_encode(['success' => false, 'error' => 'Highlight not found']);
            exit();
        }

        $highlight['imageUrls'] = json_decode($highlight['image_urls'], true) ?? [];
        $highlight['date'] = intval($highlight['date']) * 1000;
        unset($highlight['image_urls']);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'highlight' => $highlight
        ]);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to fetch highlight: ' . $e->getMessage()]);
        exit();
    }
}

// DELETE HIGHLIGHT
elseif ($method === 'DELETE' && $action === 'delete') {
    try {
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

        $authUserId = $userData['user_id'];

        $highlightId = $_GET['highlightId'] ?? '';

        if (empty($highlightId)) {
            http_response_code(400);
            echo json_encode(['success' => false, 'error' => 'Highlight ID required']);
            exit();
        }

        // Verify ownership
        $stmt = $db->prepare("SELECT user_id FROM highlights WHERE id = ?");
        $stmt->execute([$highlightId]);
        $highlight = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$highlight) {
            http_response_code(404);
            echo json_encode(['success' => false, 'error' => 'Highlight not found']);
            exit();
        }

        if ($highlight['user_id'] !== $authUserId) {
            http_response_code(403);
            echo json_encode(['success' => false, 'error' => 'Not authorized to delete this highlight']);
            exit();
        }

        // Delete highlight
        $stmt = $db->prepare("DELETE FROM highlights WHERE id = ?");
        $stmt->execute([$highlightId]);

        http_response_code(200);
        echo json_encode([
            'success' => true,
            'message' => 'Highlight deleted successfully'
        ]);
        exit();

    } catch (Exception $e) {
        error_log("Delete highlight error: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to delete highlight: ' . $e->getMessage()]);
        exit();
    }
}

// Invalid request
else {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid request']);
    exit();
}

