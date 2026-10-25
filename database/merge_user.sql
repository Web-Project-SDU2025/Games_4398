ALTER TABLE user
    ADD COLUMN name VARCHAR(50),
    ADD COLUMN gender VARCHAR(2),
    ADD COLUMN birthday VARCHAR(10),
    ADD COLUMN email VARCHAR(60),
    ADD COLUMN phone VARCHAR(20),
    ADD COLUMN address VARCHAR(20),
    ADD COLUMN introduce VARCHAR(1000),
    ADD COLUMN photo LONGBLOB,
    ADD COLUMN signature VARCHAR(200);

UPDATE user
JOIN person ON person.person_id = user.person_id
LEFT JOIN game_user ON game_user.person_id = user.person_id
SET user.name = person.name,
    user.gender = person.gender,
    user.birthday = person.birthday,
    user.email = person.email,
    user.phone = person.phone,
    user.address = person.address,
    user.introduce = person.introduce,
    user.photo = person.photo,
    user.signature = game_user.signature;

ALTER TABLE user CHANGE person_id user_id INT NOT NULL AUTO_INCREMENT;
ALTER TABLE user_game DROP FOREIGN KEY fk_library_user;
ALTER TABLE user_game ADD CONSTRAINT fk_library_user FOREIGN KEY (user_id) REFERENCES user(user_id);
ALTER TABLE game_favorite DROP FOREIGN KEY fk_favorite_user;
ALTER TABLE game_favorite ADD CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES user(user_id);
ALTER TABLE game_record DROP FOREIGN KEY fk_record_user;
ALTER TABLE game_record ADD CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES user(user_id);
