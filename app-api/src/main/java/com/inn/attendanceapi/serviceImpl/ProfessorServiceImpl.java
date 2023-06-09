package com.inn.attendanceapi.serviceImpl;

import com.inn.attendanceapi.FactoryPattern.ProfessorFactory;
import com.inn.attendanceapi.FactoryPattern.UserFactory;
import com.inn.attendanceapi.constants.SystemCst;
import com.inn.attendanceapi.dao.UserDao;
import com.inn.attendanceapi.jwt.JwtFilter;
import com.inn.attendanceapi.jwt.JwtUtil;
import com.inn.attendanceapi.model.User;
import com.inn.attendanceapi.service.ProfessorService;
import com.inn.attendanceapi.utils.SystemUtils;
import com.inn.attendanceapi.wrapper.UserWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ProfessorServiceImpl implements ProfessorService {

    @Autowired
    UserDao userDao;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    JwtFilter jwtFilter;

    @Override
    public ResponseEntity<String> addNewProfessor(Map<String, String> requestMap) {
        log.info("Inside addNewProfessor {}",requestMap);
        try {
            if(jwtFilter.isAdmin()){
                if (validateSignupMap(requestMap)) {
                    User user = userDao.findByEmailId(requestMap.get("email")); //objet persistent, when i do save he modifier en base de donne, to rendre objet simple on va detacher
                    if (Objects.isNull(user)) {
                        userDao.save(getUserFromMap(requestMap));
                        return SystemUtils.getResponseEntity("Successfully Registered", HttpStatus.OK);
                    } else {
                        return SystemUtils.getResponseEntity("Email already exits", HttpStatus.BAD_REQUEST);
                    }
                } else {
                    return SystemUtils.getResponseEntity(SystemCst.INVALID_DATA, HttpStatus.BAD_REQUEST);
                }
            }else{
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS, HttpStatus.UNAUTHORIZED);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean validateSignupMap(Map<String,String> requestMap){
        if(requestMap.containsKey("firstName") && requestMap.containsKey("lastName") && requestMap.containsKey("rfid") && requestMap.containsKey("contactNumber") && requestMap.containsKey("email") && requestMap.containsKey("password")){
            return true;
        }
        return false;
    }

    private User getUserFromMap(Map<String,String> requestMap){
        UserFactory professorFactory = new ProfessorFactory();
        User professor = professorFactory.createUser();
        professor.setFirstName(requestMap.get("firstName"));
        professor.setLastName(requestMap.get("lastName"));
        professor.setRfid(requestMap.get("rfid"));
        professor.setContactNumber(requestMap.get("contactNumber"));
        professor.setEmail(requestMap.get("email"));
        professor.setPassword(requestMap.get("password"));
        professor.setStatus("DEACTIVATED");
        professor.setRole(UserFactory.UserRole.PROFESSOR);
        return professor;
    }

    @Override
    public ResponseEntity<List<UserWrapper>> getAllProfessors(){
        try{
            if(jwtFilter.isAdmin()){
                return new ResponseEntity<>(userDao.getAllProfessors(),HttpStatus.OK);
            }else{
                return new ResponseEntity<>(new ArrayList<>(),HttpStatus.UNAUTHORIZED);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return new ResponseEntity<>(new ArrayList<>(),HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> updateProfessor(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isAdmin()) {
                if (validateSignupMap(requestMap)) {
                    User user = userDao.findByEmailId(requestMap.get("email")); // objet persistent, when i do save he
                    // modifier en base de donne, to rendre
                    // objet simple on va detacher
                    if (Objects.nonNull(user)) {
                        userDao.save(getUserFromMap(requestMap));
                        return SystemUtils.getResponseEntity("Successfully Registered", HttpStatus.OK);
                    } else {
                        return SystemUtils.getResponseEntity("User doesn't exist", HttpStatus.BAD_REQUEST);
                    }
                } else {
                    return SystemUtils.getResponseEntity(SystemCst.INVALID_DATA, HttpStatus.BAD_REQUEST);
                }
            } else {
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS, HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);

    }
}
