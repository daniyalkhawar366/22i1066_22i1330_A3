<?php
require_once 'config.php';

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'POST') {
    $action = $_GET['action'] ?? '';

    if ($action === 'signup') {
        $data = json_decode(file_get_contents('php://input'), true);

        if (empty($data['email']) || empty($data['password'])) {
            http_response_code(400);
            echo json_encode(["error" => "Email and password required"]);
            exit();
        }

        $db = getDB();
        $stmt = $db->prepare("SELECT id FROM users WHERE email = ?");
        $stmt->execute([$data['email']]);
        if ($stmt->fetch()) {
            http_response_code(409);
            echo json_encode(["error" => "Email already registered"]);
            exit();
        }

        $userId = uniqid('user_', true);
        $passwordHash = password_hash($data['password'], PASSWORD_BCRYPT);
        $username = $data['username'] ?? explode('@', $data['email'])[0];
        $displayName = trim(($data['firstName'] ?? '') . ' ' . ($data['lastName'] ?? ''));

        $stmt = $db->prepare("INSERT INTO users (id, email, password_hash, username, first_name, last_name, display_name, dob, profile_pic_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $stmt->execute([
            $userId,
            $data['email'],
            $passwordHash,
            $username,
            $data['firstName'] ?? '',
            $data['lastName'] ?? '',
            $displayName,
            $data['dob'] ?? '',
            $data['profilePicUrl'] ?? ''
        ]);

        $token = generateJWT($userId);

        echo json_encode([
            "success" => true,
            "token" => $token,
            "userId" => $userId
        ]);
    }

    elseif ($action === 'login') {
        $data = json_decode(file_get_contents('php://input'), true);

        $db = getDB();
        $stmt = $db->prepare("SELECT id, password_hash FROM users WHERE email = ?");
        $stmt->execute([$data['email']]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$user || !password_verify($data['password'], $user['password_hash'])) {
            http_response_code(401);
            echo json_encode(["error" => "Invalid credentials"]);
            exit();
        }

        $token = generateJWT($user['id']);

        echo json_encode([
            "success" => true,
            "token" => $token,
            "userId" => $user['id']
        ]);
    }
}
?>
