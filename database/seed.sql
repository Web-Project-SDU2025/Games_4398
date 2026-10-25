INSERT INTO user_type (id, name) VALUES (1, 'ROLE_ADMIN'), (2, 'ROLE_GAME_USER');
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
