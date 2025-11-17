<?php
// Set upload limits
ini_set('upload_max_filesize', '10M');
ini_set('post_max_size', '10M');
ini_set('memory_limit', '128M');

require_once 'config.php';

error_log("Upload request received - Method: " . $_SERVER['REQUEST_METHOD']);
error_log("Max file size: " . ini_get('upload_max_filesize'));
error_log("Post max size: " . ini_get('post_max_size'));

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    error_log("POST data: " . print_r($_POST, true));
    error_log("FILES data: " . print_r($_FILES, true));

    if (!isset($_FILES['file'])) {
        error_log("No file uploaded in request");
        echo json_encode(['success' => false, 'error' => 'No file uploaded']);
        exit();
    }

    $file = $_FILES['file'];
    $userId = $_POST['user_id'] ?? uniqid('user_', true);
    
    error_log("Processing upload for user: $userId");
    error_log("File details - Name: {$file['name']}, Type: {$file['type']}, Size: {$file['size']}, Error: {$file['error']}");

    if ($file['error'] !== UPLOAD_ERR_OK) {
        $errorMsg = "Upload error code: " . $file['error'];
        error_log($errorMsg);
        echo json_encode(['success' => false, 'error' => $errorMsg]);
        exit();
    }

    $allowedTypes = ['image/jpeg', 'image/png', 'image/jpg'];
    if (!in_array($file['type'], $allowedTypes)) {
        error_log("Invalid file type: {$file['type']}");
        echo json_encode(['success' => false, 'error' => 'Invalid file type. Allowed: jpeg, jpg, png']);
        exit();
    }

    if ($file['size'] > 10 * 1024 * 1024) {
        error_log("File too large: {$file['size']} bytes");
        echo json_encode(['success' => false, 'error' => 'File too large. Max 10MB']);
        exit();
    }

    $uploadDir = __DIR__ . '/../uploads/';
    if (!is_dir($uploadDir)) {
        error_log("Creating upload directory: $uploadDir");
        mkdir($uploadDir, 0777, true);
    }

    $extension = pathinfo($file['name'], PATHINFO_EXTENSION);
    $filename = $userId . '_' . time() . '.' . $extension;
    $destination = $uploadDir . $filename;

    error_log("Attempting to move file to: $destination");

    if (move_uploaded_file($file['tmp_name'], $destination)) {
        $fileUrl = "http://192.168.18.55/backend/uploads/" . $filename;
        error_log("Upload successful: $fileUrl");
        echo json_encode([
            'success' => true,
            'url' => $fileUrl
        ]);
    } else {
        error_log("move_uploaded_file failed from {$file['tmp_name']} to $destination");
        echo json_encode(['success' => false, 'error' => 'Upload failed - could not move file']);
    }
} else {
    error_log("Invalid request method: " . $_SERVER['REQUEST_METHOD']);
    echo json_encode(['success' => false, 'error' => 'Invalid request method']);
}
?>
