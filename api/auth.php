<?php
require_once __DIR__ . '/../config.php';

function generateJWT($userId) {
    $header = json_encode(['typ' => 'JWT', 'alg' => 'HS256']);
    $payload = json_encode([
        'user_id' => $userId,
        'exp' => time() + (7 * 24 * 60 * 60) // 7 days
    ]);
    
    $base64UrlHeader = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($header));
    $base64UrlPayload = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($payload));
    
    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, JWT_SECRET, true);
    $base64UrlSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));
    
    return $base64UrlHeader . "." . $base64UrlPayload . "." . $base64UrlSignature;
}



$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

if ($action === 'test') {
    echo json_encode([
        'success' => true,
        'message' => 'Backend working',
        'timestamp' => time()
    ]);
    exit();
}

if ($method === 'POST') {
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);

    if ($data === null) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Invalid JSON']);
        exit();
    }

    if ($action === 'signup') {
        if (empty($data['email']) || empty($data['password'])) {
            http_response_code(400);
            echo json_encode(['success' => false, 'error' => 'Email and password required']);
            exit();
        }

        // Check existing email
        $stmt = $db->prepare("SELECT id FROM users WHERE email = ?");
        $stmt->bind_param("s", $data['email']);
        $stmt->execute();
        $result = $stmt->get_result();
        
        if ($result->num_rows > 0) {
            http_response_code(409);
            echo json_encode(['success' => false, 'error' => 'Email already registered']);
            exit();
        }

        $userId = uniqid('user_', true);
        $passwordHash = password_hash($data['password'], PASSWORD_BCRYPT);
        $username = $data['username'] ?? explode('@', $data['email'])[0];
        $displayName = trim(($data['firstName'] ?? '') . ' ' . ($data['lastName'] ?? ''));

        $stmt = $db->prepare("INSERT INTO users (id, email, password_hash, username, first_name, last_name, display_name, dob, profile_pic_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $stmt->bind_param("sssssssss",
            $userId,
            $data['email'],
            $passwordHash,
            $username,
            $data['firstName'] ?? '',
            $data['lastName'] ?? '',
            $displayName,
            $data['dob'] ?? '',
            $data['profilePicUrl'] ?? ''
        );
        $stmt->execute();

        $token = generateJWT($userId);

        echo json_encode([
            'success' => true,
            'token' => $token,
            'userId' => $userId
        ]);
    }
    
    elseif ($action === 'login') {
        if (empty($data['email']) || empty($data['password'])) {
            http_response_code(400);
            echo json_encode(['success' => false, 'error' => 'Email and password required']);
            exit();
        }

        $stmt = $db->prepare("SELECT id, password_hash FROM users WHERE email = ?");
        $stmt->bind_param("s", $data['email']);
        $stmt->execute();
        $result = $stmt->get_result();
        $user = $result->fetch_assoc();

        if (!$user || !password_verify($data['password'], $user['password_hash'])) {
            http_response_code(401);
            echo json_encode(['success' => false, 'error' => 'Invalid credentials']);
            exit();
        }

        $token = generateJWT($user['id']);

        echo json_encode([
            'success' => true,
            'token' => $token,
            'userId' => $user['id']
        ]);
    }
    
    else {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Invalid action']);
    }
}
?>
