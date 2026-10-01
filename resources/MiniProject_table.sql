create database mini;
use mini;
-- =========================================
-- User
-- =========================================

CREATE TABLE User (
    ID VARCHAR(20) NOT NULL,
    Name VARCHAR(10) NOT NULL,
    PassWord VARCHAR(20) NOT NULL,
    NickName VARCHAR(10) NOT NULL,
    Phone VARCHAR(13) NOT NULL,

    CONSTRAINT PK_User PRIMARY KEY (ID),
     CONSTRAINT UQ_User_Phone UNIQUE (Phone)
);


-- =========================================
-- BigCategory
-- =========================================

CREATE TABLE BigCategory (
    BigCategoryCode VARCHAR(10) NOT NULL,
    Category VARCHAR(10) NOT NULL,

    CONSTRAINT PK_BigCategory PRIMARY KEY (BigCategoryCode)
);


-- =========================================
-- SmallCategory
-- =========================================

CREATE TABLE SmallCategory (
    SmallCategoryCode VARCHAR(10) NOT NULL,
    BigCategoryCode VARCHAR(10) NOT NULL,
    Category VARCHAR(10) NOT NULL,

    CONSTRAINT PK_SmallCategory PRIMARY KEY (SmallCategoryCode),

    CONSTRAINT FK_SmallCategory_BigCategory
        FOREIGN KEY (BigCategoryCode)
        REFERENCES BigCategory(BigCategoryCode)
);


-- =========================================
-- Item
-- =========================================

CREATE TABLE Item (
    ItemNum INT NOT NULL AUTO_INCREMENT,
    LenderID VARCHAR(20) NOT NULL,
    ItemName VARCHAR(50) NOT NULL,
    Status boolean NOT NULL DEFAULT TRUE,
    SmallCategoryCode VARCHAR(10) NOT NULL,

    CONSTRAINT PK_Item PRIMARY KEY (ItemNum),

    CONSTRAINT FK_Item_User
        FOREIGN KEY (LenderID)
        REFERENCES User(ID),

    CONSTRAINT FK_Item_SmallCategory
        FOREIGN KEY (SmallCategoryCode)
        REFERENCES SmallCategory(SmallCategoryCode)
);


-- =========================================
-- Post
-- =========================================

CREATE TABLE Post (
    PostNum INT NOT NULL AUTO_INCREMENT,
    ItemNum INT NOT NULL,
    Title VARCHAR(50) NOT NULL,
    Content LONGTEXT NOT NULL,
    CreateAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UpdateAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    RentDate DATETIME NOT NULL,
    ReturnDate DATETIME NOT NULL,
    Addr VARCHAR(255) NOT NULL,

    CONSTRAINT PK_Post PRIMARY KEY (PostNum),

    CONSTRAINT UQ_Post_Item UNIQUE (ItemNum),

    CONSTRAINT FK_Post_Item
        FOREIGN KEY (ItemNum)
        REFERENCES Item(ItemNum)
);


-- =========================================
-- Rental
-- =========================================

CREATE TABLE Rental (
    RentalNum INT NOT NULL AUTO_INCREMENT,
    BorrowerID VARCHAR(20) NOT NULL,
    Status INT NOT NULL DEFAULT 100,
    Postnum INT NOT NULL,

    CONSTRAINT PK_Rental PRIMARY KEY (RentalNum),

    CONSTRAINT FK_Rental_User
        FOREIGN KEY (BorrowerID)
        REFERENCES User(ID),

    CONSTRAINT FK_Rental_Post
        FOREIGN KEY (PostNum)
        REFERENCES Post(PostNum)
);


-- =========================================
-- Admin
-- =========================================

CREATE TABLE Admin (
    ID VARCHAR(20) NOT NULL,
    PassWord VARCHAR(20) NOT NULL,
    Name VARCHAR(10) NOT NULL,

    CONSTRAINT PK_Admin PRIMARY KEY (ID)
);

DROP VIEW IF EXISTS View_Rental_Info;

CREATE or replace view View_Rental_Info AS
SELECT
	r.RentalNum,
    r.BorrowerID,
    b.NickName AS BorrowerNickName,
    b.Phone AS BorrowerPhone,
    i.ItemName,
    p.ReturnDate,
    p.Addr,
    r.Status,
    i.LenderID,
    l.NickName AS LenderNickName,
    l.Phone AS LenderPhone
FROM Rental r
JOIN Post p ON r.PostNum = p.PostNum
JOIN Item i ON p.ItemNum = i.ItemNum
JOIN User l ON i.LenderID = l.ID
JOIN User b ON r.BorrowerID = b.ID;
    
CREATE or replace view View_Rental_LenderID AS
SELECT
	r.RentalNum,
    r.BorrowerID,
    r.Status,
	r.PostNum,
    i.LenderID
FROM Rental r
JOIN Post p ON r.PostNum = p.PostNum
JOIN Item i ON p.ItemNum = i.ItemNum
JOIN User l ON i.LenderID = l.ID;
select *  from View_rental_lenderID;

drop view View_Available_Post;
CREATE OR REPLACE VIEW View_Available_Post AS
SELECT 
    p.PostNum,
    p.Title,
    p.Content,
    p.RentDate,
    p.ReturnDate,
    p.Addr,
    i.ItemName,
    s.Category
FROM Post p
JOIN Item i
    ON p.ItemNum = i.ItemNum
JOIN SmallCategory s
    ON i.SmallCategoryCode = s.SmallCategoryCode
WHERE i.Status = TRUE;

select * from item ;
select * 
from rental where status not in (101, 200, 201, 210, 211);
select * from View_Available_Post;

