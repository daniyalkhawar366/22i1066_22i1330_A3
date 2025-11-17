<?php
// Simple test to check if upload.php is accessible
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

$uploadDir = __DIR__ . '/../uploads/';

echo json_encode([
    'success' => true,
    'message' => 'Upload endpoint is reachable',
    'upload_dir_exists' => is_dir($uploadDir),
    'upload_dir_writable' => is_writable($uploadDir),
    'upload_dir_path' => $uploadDir,
    'max_upload_size' => ini_get('upload_max_filesize'),
    'max_post_size' => ini_get('post_max_size'),
    'server_time' => date('Y-m-d H:i:s')
]);
?>

