<?php
require_once 'config.php';

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!isset($_FILES['file'])) {
        echo json_encode(['success' => false, 'error' => 'No file uploaded']);
        exit();
    }

    $file = $_FILES['file'];
    $userId = $_POST['user_id'] ?? uniqid('user_', true);
    
    $allowedTypes = ['image/jpeg', 'image/png', 'image/jpg'];
    if (!in_array($file['type'], $allowedTypes)) {
        echo json_encode(['success' => false, 'error' => 'Invalid file type']);
        exit();
    }

    if ($file['size'] > 5 * 1024 * 1024) {
        echo json_encode(['success' => false, 'error' => 'File too large']);
        exit();
    }

    $uploadDir = __DIR__ . '/../uploads/';
    if (!is_dir($uploadDir)) {
        mkdir($uploadDir, 0777, true);
    }

    $extension = pathinfo($file['name'], PATHINFO_EXTENSION);
    $filename = $userId . '_' . time() . '.' . $extension;
    $destination = $uploadDir . $filename;

    if (move_uploaded_file($file['tmp_name'], $destination)) {
        $fileUrl = "http://192.168.18.55/backend/uploads/" . $filename;
        echo json_encode([
            'success' => true,
            'url' => $fileUrl
        ]);
    } else {
        echo json_encode(['success' => false, 'error' => 'Upload failed']);
    }
}
?>
