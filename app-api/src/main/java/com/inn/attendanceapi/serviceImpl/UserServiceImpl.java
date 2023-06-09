package com.inn.attendanceapi.serviceImpl;

import com.google.common.base.Strings;
import com.inn.attendanceapi.constants.SystemCst;
import com.inn.attendanceapi.dao.UserDao;
import com.inn.attendanceapi.jwt.JwtFilter;
import com.inn.attendanceapi.jwt.JwtUtil;
import com.inn.attendanceapi.jwt.UsersDetailsService;
import com.inn.attendanceapi.model.User;
import com.inn.attendanceapi.service.UserService;
import com.inn.attendanceapi.utils.EmailUtils;
import com.inn.attendanceapi.utils.SystemUtils;
import com.inn.attendanceapi.wrapper.UserWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserDao userDao;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UsersDetailsService customerUsersDetailsService;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JwtFilter jwtFilter;

    @Autowired
    EmailUtils emailUtils;




    @Override
    public ResponseEntity<String> login(Map<String, String> requestMap) {
        log.info("Inside login");
        try{
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requestMap.get("email"),requestMap.get("password"))
            );
            if(auth.isAuthenticated()){
                if(customerUsersDetailsService.getUserDetail().getStatus().equalsIgnoreCase("ACTIVE")){
                    return new ResponseEntity<String>("{\"token\":\""+
                            jwtUtil.generateToken(customerUsersDetailsService.getUserDetail().getEmail(),
                                    String.valueOf(customerUsersDetailsService.getUserDetail().getRole()))+"\"}",
                            HttpStatus.OK);
                }
                else{
                    return new ResponseEntity<String>("{\"message\":\""+"Wait for admin approval."+"\"}",
                            HttpStatus.BAD_REQUEST);
                }
            }
        }catch(Exception e){
            log.error("{}",e);
        }
        return new ResponseEntity<String>("{\"message\":\""+"Bad Credentials."+"\"}",
                HttpStatus.BAD_REQUEST);
    }

    @Override
    public ResponseEntity<String> update(Map<String, String> requestMap) {
        try{
            if(jwtFilter.isAdmin()){
                Optional<User> optional = userDao.findById(Integer.parseInt(requestMap.get("id")));
                if(!optional.isEmpty() && requestMap.containsKey("status")){
                    String status = requestMap.get("status");
                    if (!status.equalsIgnoreCase("ACTIVE") && !status.equalsIgnoreCase("DEACTIVATED")) {
                        return SystemUtils.getResponseEntity("Invalid status value", HttpStatus.BAD_REQUEST);
                    };
                    userDao.updateStatus(requestMap.get("status"),Integer.parseInt(requestMap.get("id")));
                    sendMailToAllAdmin(requestMap.get("status"),optional.get().getEmail(),userDao.getAllAdmin());

                    return SystemUtils.getResponseEntity("User Status Updated Successfully",HttpStatus.OK);
                }else{
                    return SystemUtils.getResponseEntity("User id doesn't not exist",HttpStatus.OK);
                }
            }
            else{
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS,HttpStatus.UNAUTHORIZED);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> checkToken() {
        return  SystemUtils.getResponseEntity("true",HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> changePassword(Map<String, String> requestMap) {
        try {
            User userObj = userDao.findByEmail(jwtFilter.getCurrentUser());
            if(!userObj.equals(null)){
                if(userObj.getPassword().equals(requestMap.get("oldPassword"))){
                    userObj.setPassword(requestMap.get("newPassword"));
                    userDao.save(userObj);
                    return SystemUtils.getResponseEntity("Password Updated Successfully",HttpStatus.OK);
                }
                return SystemUtils.getResponseEntity("Incorrect Old Password",HttpStatus.BAD_REQUEST);
            }
            return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
        }catch (Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> forgotPassword(Map<String, String> requestMap) {
        try {
            User user = userDao.findByEmail(requestMap.get("email"));
            if(!Objects.isNull(user) && !Strings.isNullOrEmpty(user.getEmail()))
                emailUtils.forgotMail(user.getEmail(),"Credentials by Attendance Management System",user.getPassword());
            return SystemUtils.getResponseEntity("Check your mail for Credentials.",HttpStatus.OK);
        }catch (Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG,HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void sendMailToAllAdmin(String status, String user, List<String> allAdmin) {
        allAdmin.remove(jwtFilter.getCurrentUser());
        if(status != null  && status.equalsIgnoreCase("ACTIVE")){
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(),"Account Approved","USER:~ "+user+"\n is approved by \nADMIN:~"+jwtFilter.getCurrentUser(),allAdmin);
        }else {
            emailUtils.sendSimpleMessage(jwtFilter.getCurrentUser(),"Account Disabled","USER:~ "+user+"\n is disabled by \nADMIN:~"+jwtFilter.getCurrentUser(),allAdmin);
        }
    }

    @Override
    public ResponseEntity<String> deleteUser(Integer id) {
        if (id == null)
            return SystemUtils.getResponseEntity("user id is null", HttpStatus.BAD_REQUEST);

        userDao.deleteById(id);

        return SystemUtils.getResponseEntity("user deleted", HttpStatus.OK);

    }
}
