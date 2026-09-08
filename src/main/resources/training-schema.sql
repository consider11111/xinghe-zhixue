CREATE TABLE IF NOT EXISTS app_seed (
  name VARCHAR(80) PRIMARY KEY
);
INSERT IGNORE INTO school_class(code,name,grade) VALUES
 ('CLASS-003','高一（3）班','高一'),('CLASS-005','高一（5）班','高一');
INSERT IGNORE INTO user_class(user_id,class_id)
 SELECT u.id,c.id FROM sys_user u CROSS JOIN school_class c
 WHERE u.enabled=1 AND ((u.username='student' AND c.code='CLASS-003') OR
 (u.username='teacher' AND c.code IN ('CLASS-003','CLASS-005')))
 AND NOT EXISTS (SELECT 1 FROM app_seed WHERE name='initial-class-memberships');
INSERT IGNORE INTO app_seed(name) VALUES('initial-class-memberships');

CREATE TABLE IF NOT EXISTS training_day (
 class_id BIGINT NOT NULL,
 plan_date DATE NOT NULL,
 PRIMARY KEY(class_id,plan_date),
 FOREIGN KEY(class_id) REFERENCES school_class(id)
);
CREATE TABLE IF NOT EXISTS class_training (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 class_id BIGINT NOT NULL,
 plan_date DATE NOT NULL,
 subject VARCHAR(30) NOT NULL,
 title VARCHAR(100) NOT NULL,
 content TEXT NOT NULL,
 question_count INT NOT NULL,
 minutes INT NOT NULL,
 source VARCHAR(20) NOT NULL DEFAULT 'random',
 FOREIGN KEY(class_id) REFERENCES school_class(id)
);
CREATE TABLE IF NOT EXISTS training_progress (
 training_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 completed TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY(training_id,user_id),
 FOREIGN KEY(training_id) REFERENCES class_training(id) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
