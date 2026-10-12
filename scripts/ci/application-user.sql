-- Credentials and privileges for the disposable CI database only.
CREATE USER IF NOT EXISTS 'crave_app'@'%' IDENTIFIED BY 'ci-app-password';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON crave.* TO 'crave_app'@'%';
