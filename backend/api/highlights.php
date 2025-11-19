<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../middleware/auth.php';

try {
    $database = new Database();
    $db = $database->getConnection();
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
        $authUserId = verifyToken();
    } catch (Exception $e) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized: ' . $e->getMessage()]);
        exit();
    }

    if (!$authUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit();
    }

    $input = file_get_contents('php://input');
    $data = json_decode($input, true);

    if (empty($data['title']) || empty($data['imageUrls']) || empty($data['date'])) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Title, images, and date required']);
        exit();
    }

    try {
        $highlightId = uniqid('highlight_', true);
        $imageUrlsJson = json_encode($data['imageUrls']);
        $date = intval($data['date']); // Unix timestamp

        $stmt = $db->prepare("INSERT INTO highlights (id, user_id, title, image_urls, date, created_at) VALUES (?, ?, ?, ?, FROM_UNIXTIME(?), NOW())");
        $stmt->bind_param('ssssi', $highlightId, $authUserId, $data['title'], $imageUrlsJson, $date);
        $stmt->execute();
        $stmt->close();

        echo json_encode([
            'success' => true,
            'highlightId' => $highlightId,
            'message' => 'Highlight created successfully'
        ]);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to create highlight: ' . $e->getMessage()]);
        exit();
    }
}

// GET USER HIGHLIGHTS
if ($method === 'GET' && $action === 'getUserHighlights') {
    $userId = $_GET['userId'] ?? '';

    if (empty($userId)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'User ID required']);
        exit();
    }

    try {
        $stmt = $db->prepare("SELECT id, user_id, title, image_urls, UNIX_TIMESTAMP(date) as date FROM highlights WHERE user_id = ? ORDER BY date DESC");
        $stmt->bind_param('s', $userId);
        $stmt->execute();
        $result = $stmt->get_result();
        $highlights = $result->fetch_all(MYSQLI_ASSOC);
        $stmt->close();

        // Parse JSON image_urls
        foreach ($highlights as &$highlight) {
            $highlight['imageUrls'] = json_decode($highlight['image_urls'], true) ?? [];
            unset($highlight['image_urls']);
        }

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
if ($method === 'GET' && $action === 'getHighlight') {
    $highlightId = $_GET['highlightId'] ?? '';

    if (empty($highlightId)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Highlight ID required']);
        exit();
    }

    try {
        $stmt = $db->prepare("SELECT id, user_id, title, image_urls, UNIX_TIMESTAMP(date) as date FROM highlights WHERE id = ?");
        $stmt->bind_param('s', $highlightId);
        $stmt->execute();
        $result = $stmt->get_result();
        $highlight = $result->fetch_assoc();
        $stmt->close();

        if (!$highlight) {
            http_response_code(404);
            echo json_encode(['success' => false, 'error' => 'Highlight not found']);
            exit();
        }

        $highlight['imageUrls'] = json_decode($highlight['image_urls'], true) ?? [];
        unset($highlight['image_urls']);

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
if ($method === 'DELETE' && $action === 'delete') {
    try {
        $authUserId = verifyToken();
    } catch (Exception $e) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized: ' . $e->getMessage()]);
        exit();
    }

    if (!$authUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit();
    }

    $highlightId = $_GET['highlightId'] ?? '';

    if (empty($highlightId)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Highlight ID required']);
        exit();
    }

    try {
        // Verify ownership
        $stmt = $db->prepare("SELECT user_id FROM highlights WHERE id = ?");
        $stmt->bind_param('s', $highlightId);
        $stmt->execute();
        $result = $stmt->get_result();
        $highlight = $result->fetch_assoc();
        $stmt->close();

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
        $stmt->bind_param('s', $highlightId);
        $stmt->execute();
        $stmt->close();

        echo json_encode([
            'success' => true,
            'message' => 'Highlight deleted successfully'
        ]);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to delete highlight: ' . $e->getMessage()]);
        exit();
    }
}

http_response_code(400);
echo json_encode(['success' => false, 'error' => 'Invalid request']);
?>

