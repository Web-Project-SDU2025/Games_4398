SET NAMES utf8mb4;

DROP TABLE family_member;
DROP TABLE fee;
DROP TABLE student_statistics;
DROP TABLE student_leave;
DROP TABLE score;
DROP TABLE teacher;

ALTER TABLE student RENAME TO game_user;
ALTER TABLE game_user DROP COLUMN major, DROP COLUMN class_name;
ALTER TABLE game_user ADD COLUMN signature VARCHAR(200);
INSERT INTO game_user (person_id) SELECT person_id FROM user WHERE person_id NOT IN (SELECT person_id FROM game_user);

ALTER TABLE course DROP FOREIGN KEY FK17gwkcq6pmnubgtpk3yebx740;
DELETE FROM course;
ALTER TABLE course RENAME TO game;
ALTER TABLE game CHANGE course_id game_id INT NOT NULL AUTO_INCREMENT;
ALTER TABLE game DROP COLUMN credit, DROP COLUMN pre_course_id;
ALTER TABLE game CHANGE course_path game_path VARCHAR(255);
ALTER TABLE game ADD COLUMN category VARCHAR(50), ADD COLUMN description VARCHAR(1000);
ALTER TABLE game ADD UNIQUE KEY uk_game_num (num);

CREATE TABLE user_game (
    user_id INT NOT NULL,
    game_id INT NOT NULL,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, game_id),
    CONSTRAINT fk_library_user FOREIGN KEY (user_id) REFERENCES game_user(person_id),
    CONSTRAINT fk_library_game FOREIGN KEY (game_id) REFERENCES game(game_id)
);

CREATE TABLE game_favorite (
    user_id INT NOT NULL,
    game_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, game_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES game_user(person_id),
    CONSTRAINT fk_favorite_game FOREIGN KEY (game_id) REFERENCES game(game_id)
);

CREATE TABLE game_record (
    record_id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    game_id INT NOT NULL,
    score INT NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    played_at DATETIME NOT NULL,
    PRIMARY KEY (record_id),
    CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES game_user(person_id),
    CONSTRAINT fk_record_game FOREIGN KEY (game_id) REFERENCES game(game_id)
);

UPDATE user_type SET name = 'ROLE_GAME_USER' WHERE id = 2;
UPDATE user SET user_type_id = 2 WHERE user_type_id = 3;
DELETE FROM user_type WHERE id = 3;
UPDATE person SET type = '1' WHERE type = '2';
ALTER TABLE person DROP COLUMN dept, DROP COLUMN card;

DELETE FROM menu;
INSERT INTO menu (id, name, title, pid, user_type_ids) VALUES
(1, NULL, '我的账户', NULL, '1,2'),
(2, NULL, '系统管理', NULL, '1'),
(3, NULL, '游戏中心', NULL, '1,2'),
(11, 'MainPage', '网站概览', 1, '1,2'),
(12, 'Password', '修改密码', 1, '1,2'),
(13, 'GameUserProfile', '个人资料', 1, '1,2'),
(21, 'MenuManage', '菜单管理', 2, '1'),
(22, 'DictionaryManage', '字典管理', 2, '1'),
(23, 'GameUserManage', '用户管理', 2, '1'),
(31, 'GameCatalog', '游戏列表', 3, '1,2'),
(32, 'GameLibrary', '我的游戏库', 3, '1,2'),
(33, 'GameFavorites', '我的收藏', 3, '1,2'),
(34, 'GameRecords', '游戏记录', 3, '1,2');
