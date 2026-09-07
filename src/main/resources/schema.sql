CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('student','teacher','admin') NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS student_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL UNIQUE,
  grade VARCHAR(20), class_name VARCHAR(50), student_no VARCHAR(50),
  FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS teacher_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL UNIQUE,
  subject VARCHAR(30), teacher_no VARCHAR(50),
  FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS admin_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL UNIQUE,
  admin_no VARCHAR(50), FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS school_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  grade VARCHAR(20) NOT NULL DEFAULT '',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_class(user_id, class_id),
  FOREIGN KEY (user_id) REFERENCES sys_user(id),
  FOREIGN KEY (class_id) REFERENCES school_class(id)
);
INSERT INTO sys_user(username,password_hash,role,display_name) VALUES
 ('student','123456','student','林同学'),
 ('teacher','123456','teacher','王老师'),
 ('admin','123456','admin','系统管理员')
ON DUPLICATE KEY UPDATE username=VALUES(username);
INSERT IGNORE INTO student_profile(user_id) SELECT id FROM sys_user WHERE username='student';
INSERT IGNORE INTO teacher_profile(user_id) SELECT id FROM sys_user WHERE username='teacher';
INSERT IGNORE INTO admin_profile(user_id) SELECT id FROM sys_user WHERE username='admin';
