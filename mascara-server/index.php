<?php


# Logs request data.
# `cd` to this file's directory and run `sudo php -S 0.0.0.0:80` to start the server


function write($msg) {
    $stdout = fopen("php://stdout", "w");
    fputs($stdout, $msg . "\n");
    fclose($stdout);
}

write("\n======== " . date("H:i:s") . " ========");
foreach ($_REQUEST as $key => $value) {
    if ($key === "picture") {
        write("$key: " . strlen($value) . " bytes");
        file_put_contents('capture.jpg', base64_decode($value));
    } else if ($key === "audio") {
                 write("$key: " . strlen($value) . " bytes");
                 file_put_contents('recorded'.date('_h_i_s').'.mp4', base64_decode($value));
                 
           }
           else {
                 write("$key: $value");
           }
   
}
write("-------- " . date("H:i:s") . " --------\n");
?>
