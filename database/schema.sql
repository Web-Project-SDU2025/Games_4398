-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: 202.194.14.120    Database: java_1_02
-- ------------------------------------------------------
-- Server version	8.0.35-0ubuntu0.23.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `dictionary`
--

DROP TABLE IF EXISTS `dictionary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dictionary` (
  `id` int NOT NULL AUTO_INCREMENT,
  `value` varchar(40) DEFAULT NULL,
  `label` varchar(40) DEFAULT NULL,
  `pid` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `game`
--

DROP TABLE IF EXISTS `game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game` (
  `game_id` int NOT NULL AUTO_INCREMENT,
  `num` varchar(20) NOT NULL,
  `name` varchar(50) DEFAULT NULL,
  `game_path` varchar(255) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`game_id`),
  UNIQUE KEY `uk_game_num` (`num`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `game_favorite`
--

DROP TABLE IF EXISTS `game_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_favorite` (
  `user_id` int NOT NULL,
  `game_id` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`game_id`),
  KEY `fk_favorite_game` (`game_id`),
  CONSTRAINT `fk_favorite_game` FOREIGN KEY (`game_id`) REFERENCES `game` (`game_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `game_record`
--

DROP TABLE IF EXISTS `game_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_record` (
  `record_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `game_id` int NOT NULL,
  `score` int NOT NULL,
  `duration_seconds` int NOT NULL DEFAULT '0',
  `played_at` datetime NOT NULL,
  PRIMARY KEY (`record_id`),
  KEY `fk_record_game` (`game_id`),
  KEY `fk_record_user` (`user_id`),
  CONSTRAINT `fk_record_game` FOREIGN KEY (`game_id`) REFERENCES `game` (`game_id`),
  CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `menu`
--

DROP TABLE IF EXISTS `menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `menu` (
  `id` int NOT NULL,
  `name` varchar(40) DEFAULT NULL,
  `title` varchar(40) DEFAULT NULL,
  `pid` int DEFAULT NULL,
  `user_type_ids` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modify_log`
--

DROP TABLE IF EXISTS `modify_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `modify_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `type` varchar(4) DEFAULT NULL,
  `table_name` varchar(20) DEFAULT NULL,
  `info` varchar(2000) DEFAULT NULL,
  `operator_id` int DEFAULT NULL,
  `operate_time` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `request_log`
--

DROP TABLE IF EXISTS `request_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `request_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `url` varchar(100) DEFAULT NULL,
  `username` varchar(20) DEFAULT NULL,
  `start_time` varchar(20) DEFAULT NULL,
  `request_time` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `statistics_day`
--

DROP TABLE IF EXISTS `statistics_day`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `statistics_day` (
  `day` varchar(10) NOT NULL,
  `login_count` int DEFAULT NULL,
  `request_count` int DEFAULT NULL,
  `create_count` int DEFAULT NULL,
  `modify_count` int DEFAULT NULL,
  PRIMARY KEY (`day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_info`
--

DROP TABLE IF EXISTS `system_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_info` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(40) DEFAULT NULL,
  `value` varchar(40) DEFAULT NULL,
  `des` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `user_type_id` int DEFAULT NULL,
  `user_name` varchar(20) NOT NULL,
  `password` varchar(60) NOT NULL,
  `create_time` varchar(20) DEFAULT NULL,
  `creator_id` int DEFAULT NULL,
  `last_login_time` varchar(20) DEFAULT NULL,
  `login_count` int DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `gender` varchar(2) DEFAULT NULL,
  `birthday` varchar(10) DEFAULT NULL,
  `email` varchar(60) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `address` varchar(20) DEFAULT NULL,
  `introduce` varchar(1000) DEFAULT NULL,
  `photo` longblob,
  `signature` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK4bakctviobmdk6ddh2nwg08c2` (`user_name`),
  KEY `FKlrk9xrdps0emd6d5rx5x3ib6h` (`user_type_id`),
  CONSTRAINT `FKlrk9xrdps0emd6d5rx5x3ib6h` FOREIGN KEY (`user_type_id`) REFERENCES `user_type` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_game`
--

DROP TABLE IF EXISTS `user_game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_game` (
  `user_id` int NOT NULL,
  `game_id` int NOT NULL,
  `added_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`game_id`),
  KEY `fk_library_game` (`game_id`),
  CONSTRAINT `fk_library_game` FOREIGN KEY (`game_id`) REFERENCES `game` (`game_id`),
  CONSTRAINT `fk_library_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_type`
--

DROP TABLE IF EXISTS `user_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_type` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-25 19:35:47
