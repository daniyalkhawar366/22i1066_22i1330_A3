<?php
// Simple test file to verify Apache is serving files from backend/api/
header('Content-Type: application/json');
echo json_encode([
    'success' => true,
    'message' => 'Backend API is working!',
    'path' => __FILE__,
    'time' => date('Y-m-d H:i:s')
]);
?>

