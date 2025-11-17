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

if ($method === 'GET' && $action === 'search') {
    try {
        $query = $_GET['query'] ?? '';
        $limit = intval($_GET['limit'] ?? 200);

        error_log("Search query: $query, limit: $limit, currentUserId: $currentUserId");

        if (empty(trim($query))) {
            echo json_encode(['success' => true, 'users' => []]);
            exit();
        }

        $searchTerm = '%' . $query . '%';

        // Search users by username, first_name, last_name, or display_name
        $stmt = $db->prepare("
            SELECT id, username, first_name, last_name, display_name, bio, profile_pic_url
            FROM users
            WHERE id != ?
            AND (
                username LIKE ?
                OR first_name LIKE ?
                OR last_name LIKE ?
                OR display_name LIKE ?
            )
            ORDER BY
                CASE
                    WHEN username LIKE ? THEN 1
                    WHEN display_name LIKE ? THEN 2
                    ELSE 3
                END,
                username ASC
            LIMIT ?
        ");

        $stmt->bind_param(
            "sssssssi",
            $currentUserId,
            $searchTerm,
            $searchTerm,
            $searchTerm,
            $searchTerm,
            $searchTerm,
            $searchTerm,
            $limit
        );

        $stmt->execute();
        $result = $stmt->get_result();

        $users = [];
        while ($row = $result->fetch_assoc()) {
            // Build display name
            $firstName = trim($row['first_name'] ?? '');
            $lastName = trim($row['last_name'] ?? '');
            $displayName = '';

            if (!empty($firstName) && !empty($lastName)) {
                $displayName = "$firstName $lastName";
            } elseif (!empty($firstName)) {
                $displayName = $firstName;
            } elseif (!empty($lastName)) {
                $displayName = $lastName;
            } else {
                $displayName = $row['display_name'] ?? '';
            }

            $users[] = [
                'id' => $row['id'],
                'username' => $row['username'],
                'displayName' => $displayName,
                'subtitle' => $row['bio'] ?? '',
                'profilePicUrl' => $row['profile_pic_url'] ?? ''
            ];
        }

        error_log("Search found " . count($users) . " users");
        echo json_encode(['success' => true, 'users' => $users]);
        exit();

    } catch (Exception $e) {
        error_log("Exception in search: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => $e->getMessage(), 'users' => []]);
        exit();
    }
}

else {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid action']);
}
?>

