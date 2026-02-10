package com.bosch.rbcc.aftermarketpartsmanagementsystem.header;

import lombok.Data;

/**
 * 用于接收网关解析出的Token
 */
@Data
public class CommonHeaders {
    private int loginType;
    private String createUserName;
    private long expiresIn;
    private int id;
    private String email;
    private String departmentName;
    private int isAdmin;
    private int userId;
    private int version;
    private int companyId;
    private String roleIds;
    private long createTime;
    private long passwordPeriod;
    private String name;
    private String ntAccount;
    private String grantType;
    private String roleNames;
    private String username;
    private int status;
    private String sub;
    private long iat;
    private long exp;
    private int isStatementRead;
}
/*
  {
  "loginType": 2,
  "createUserName": "SYSTEM",
  "expiresIn": 1728548941130,
  "password": "$2a$10$HA5H/yhgS6Iu4IVsHIXh1.0Vno5.w8FtDeEq8DJDJ1XOK/jXRJXXK",
  "id": 6181,
  "email": "Raven.ZHENG@cn.bosch.com",
  "departmentName": "BD/SWD-FSB2",
  "isStatementRead": 1,
  "isAdmin": 1,
  "userId": 6181,
  "version": 0,
  "companyId": 1,
  "roleIds": "99,241,81,242,41,321,365,368,326,366,85,84,405,4,444,4",
  "createTime": 1721982699347,
  "passwordPeriod": 1737880299347,
  "name": "ZHENG Raven (BD/SWD-FSB2)",
  "ntAccount": "ZRN7SZH",
  "grantType": "authorization_code",
  "roleNames": "R_RBCC_AEP_Flow_DataReader,W_RBCC_AEP_BDSupport,R_RBCC_AEP_Catalog_DataReader,R_RBCC_AEP_Job_DataReader,测试添加组Role,R_RBCC_AEP_User,Test workflow 0815 NormalRole,R_RBCC_AEP_Group_DataReader,test_role_ssl,Test workflow 0815 adminrole,appid361 role,R_RBCC_AEP_LineEuipment_DataReader",
  "username": "ZRN7SZH",
  "status": 1,
  "sub": "ZRN7SZH",
  "iat": 1728462541,
  "exp": 1728548941
  }
 */