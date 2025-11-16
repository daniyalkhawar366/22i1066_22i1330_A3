<?php
ob_start();
require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/../middleware/auth.php';
ob_clean();

header('Content-Type: application/json');

try {
    $currentUserId = verifyToken();
    
    if (!$currentUserId) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }

    $action = $_GET['action'] ?? '';

    if ($action === 'getAll') {
    $stmt = $pdo->query("SELECT id, username, profile_pic_url FROM users ORDER BY username ASC");
    $users = $stmt->fetchAll(PDO::FETCH_ASSOC);

    $result = [];
    foreach ($users as $user) {
        $result[] = [
            'userId' => $user['id'],  // Changed from user_id
            'username' => $user['username'],
            'profilePic' => $user['profile_pic_url']
        ];
    }

    echo json_encode(['success' => true, 'users' => $result]);
    exit;
}


    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid action']);
    exit;

} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    exit;
}
