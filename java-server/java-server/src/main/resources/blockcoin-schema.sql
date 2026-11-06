-- 字块币增量表保留原账户与游戏表，初始化和重启均可执行
CREATE TABLE IF NOT EXISTS blockcoin_wallet (
    user_id INT NOT NULL PRIMARY KEY,
    balance BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS blockcoin_entry (
    entry_id VARCHAR(120) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(120) NOT NULL,
    game_path VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    INDEX idx_blockcoin_user_time (user_id, created_at)
);
CREATE TABLE IF NOT EXISTS reward_run (
    run_id VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    challenge VARCHAR(36) NOT NULL,
    level_number INT NOT NULL,
    level_hash VARCHAR(64) NOT NULL,
    minimum_seconds INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed BIT NOT NULL,
    INDEX idx_reward_user_time (user_id, started_at)
);

-- 通用会话与原游戏记录通过记录编号对应，旧记录和旧账本继续保留
CREATE TABLE IF NOT EXISTS game_play (
    play_id VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    game_id INT NOT NULL,
    record_id INT NOT NULL UNIQUE,
    challenge VARCHAR(36) NOT NULL,
    level_number INT,
    level_name VARCHAR(120) NOT NULL,
    level_hash VARCHAR(64),
    mode VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    minimum_seconds INT NOT NULL,
    reward_amount BIGINT NOT NULL,
    awarded_amount BIGINT NOT NULL,
    reported_milliseconds BIGINT NOT NULL,
    active_milliseconds BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    INDEX idx_play_user_started (user_id, started_at),
    INDEX idx_play_user_updated (user_id, ended_at, updated_at)
);

-- 游戏撤回单独保留操作记录，资金流水与历史购买继续保持原值
CREATE TABLE IF NOT EXISTS game_revocation (
    revocation_id VARCHAR(100) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    game_id INT NOT NULL,
    operator_id INT NOT NULL,
    reason VARCHAR(60) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    repurchased_at DATETIME(6),
    INDEX idx_revocation_access (user_id, game_id, repurchased_at)
);

-- 隐藏状态按查看账户分别保存，原记录继续用于统计与结算
CREATE TABLE IF NOT EXISTS record_visibility (
    visibility_id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    record_id INT NOT NULL,
    hidden_at DATETIME(6) NOT NULL,
    UNIQUE KEY idx_visibility_user_record (user_id, record_id)
);

-- 账户通知按接收账户隔离，接收账户为空表示全站公告
CREATE TABLE IF NOT EXISTS site_message (
    message_id VARCHAR(160) NOT NULL PRIMARY KEY,
    recipient_id INT,
    sender_id INT,
    sender_name VARCHAR(60) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(80) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_message_recipient_time (recipient_id, created_at),
    INDEX idx_message_sender_kind_time (sender_id, kind, created_at)
);
-- 公告回执由每个查看账户分别保存
CREATE TABLE IF NOT EXISTS message_receipt (
    receipt_id VARCHAR(180) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    message_id VARCHAR(160) NOT NULL,
    read_at DATETIME(6) NOT NULL,
    UNIQUE KEY idx_receipt_user_message (user_id, message_id)
);

-- 管理员对反馈的处理决定和回复与原反馈分开保存
CREATE TABLE IF NOT EXISTS feedback_resolution (
    message_id VARCHAR(160) NOT NULL PRIMARY KEY,
    decision VARCHAR(20) NOT NULL,
    administrator_id INT NOT NULL,
    reply VARCHAR(2000) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
