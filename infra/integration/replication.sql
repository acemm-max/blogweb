-- Development-only credentials for the isolated compose.yml MySQL.
CREATE USER 'canal_test'@'%' IDENTIFIED WITH mysql_native_password BY 'blogweb_test_canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal_test'@'%';
