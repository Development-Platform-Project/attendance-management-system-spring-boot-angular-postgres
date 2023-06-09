package com.inn.attendanceapi.serviceImpl;

import com.inn.attendanceapi.FactoryPattern.UserFactory;
import com.inn.attendanceapi.constants.SystemCst;
import com.inn.attendanceapi.dao.*;
import com.inn.attendanceapi.jwt.JwtFilter;
import com.inn.attendanceapi.model.*;
import com.inn.attendanceapi.service.SeanceService;
import com.inn.attendanceapi.utils.SystemUtils;
import com.inn.attendanceapi.record.PresenceRecord;
import com.inn.attendanceapi.wrapper.SeanceParticipantWrapper;
import com.inn.attendanceapi.wrapper.SeanceWrapper;
import com.inn.attendanceapi.wrapper.UserWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.rowset.serial.SerialBlob;
import java.io.File;
import java.nio.file.Files;
import java.sql.Blob;
import java.sql.Time;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
public class SeanceServiceImpl implements SeanceService {

    @Autowired
    SeanceParticipantsDao seanceParticipantsDao;
    @Autowired
    UserDao userDao;
    @Autowired
    GroupDao groupDao;
    @Autowired
    SeanceDao seanceDao;

    @Autowired
    PresenceDao presenceDao;

    @Autowired
    SemesterDao semesterDao;

    @Autowired
    ElementDao elementDao;

    @Autowired
    SalleDao salleDao;

    @Autowired
    JustificationDao justificationDao;

    @Autowired
    JwtFilter jwtFilter;

    @Override
    public ResponseEntity<String> addSeance(Map<String, String> requestMap) {
        try {
            if(jwtFilter.isAdmin()){
                if(requestMap.containsKey("date") && requestMap.containsKey("time") && requestMap.containsKey("duration") && requestMap.containsKey("salle_id") && requestMap.containsKey("type") && requestMap.containsKey("semester_id") && requestMap.containsKey("element_id")){
                    Optional<Semester> optionalSemester = semesterDao.findById(Integer.parseInt(requestMap.get("semester_id")));
                    if(optionalSemester.isPresent()){
                        Optional<Element> optionalElement = elementDao.findById(Integer.parseInt(requestMap.get("element_id")));
                        if(optionalElement.isPresent()){

                            Optional<Salle> optionalSalle = salleDao.findById(Integer.parseInt(requestMap.get("salle_id")));
                            if (optionalSalle.isPresent()) {

                                Salle salle = optionalSalle.get();
                                LocalDate date = LocalDate.parse(requestMap.get("date"));
                                Time startTime = Time.valueOf(requestMap.get("time")+":00");
                                Time duration = Time.valueOf(requestMap.get("duration")+":00");

                                if (isSeanceConflict(salle, date, startTime, duration)) {
                                    return SystemUtils.getResponseEntity("There is a conflict with an existing seance", HttpStatus.BAD_REQUEST);
                                }

                                Seance seance = getSeanceFromMap(requestMap);
                                seanceDao.save(seance);
                                return SystemUtils.getResponseEntity("Seance Participation Added Successfully", HttpStatus.OK);
                            }

                            return SystemUtils.getResponseEntity("Salle id does not exist", HttpStatus.OK);

                        }
                        return SystemUtils.getResponseEntity("Element id does not exist", HttpStatus.OK);
                    }
                    return SystemUtils.getResponseEntity("Semester id does not exist", HttpStatus.OK);
                }
            }else{
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS,HttpStatus.UNAUTHORIZED);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Seance getSeanceFromMap(Map<String, String> requestMap){
        Semester semester = new Semester();
        Element element = new Element();
        Seance seance = new Seance();
        Salle salle = new Salle();
        seance.setDate(LocalDate.parse(requestMap.get("date")));
        seance.setTime(Time.valueOf(requestMap.get("time")+":00"));
        seance.setDuration(Time.valueOf(requestMap.get("duration")+":00"));
        seance.setType(Seance.SeanceType.valueOf(requestMap.get("type")));
        semester.setId(Integer.parseInt(requestMap.get("semester_id")));
        element.setId(Integer.parseInt(requestMap.get("element_id")));
        salle.setId(Integer.valueOf(requestMap.get("salle_id")));
        seance.setSemester(semester);
        seance.setElement(element);
        seance.setSalle(salle);
        return seance;
    }


    public boolean isSeanceConflict(Salle salle, LocalDate date, Time startTime, Time duration) {
        LocalTime endTime = startTime.toLocalTime().plusMinutes(duration.getTime() / 60000);
        List<Seance> allSeances = seanceDao.findAllSeancesBySalleAndDate(salle, date);
        for (Seance seance : allSeances) {
            if (seance.conflictsWith(startTime.toLocalTime(), endTime)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public ResponseEntity<String> addParticipant(Map<String, String> requestMap) {
        try {
            if(jwtFilter.isAdmin()){
                if(requestMap.containsKey("seance_id")){

                if(requestMap.containsKey("participant_id")){
                    Optional<User> optional = userDao.findById(Integer.parseInt(requestMap.get("participant_id")));
                    if(optional.isPresent()){
                        User user = optional.get();
                        if(user.getRole() == UserFactory.UserRole.STUDENT){
                            seanceParticipantsDao.save(getSeanceParticipantFromMap(requestMap));
                            return SystemUtils.getResponseEntity("Student Participation Added Successfully", HttpStatus.OK);
                        }
                        else if (user.getRole() == UserFactory.UserRole.PROFESSOR){
                            Integer seanceId = Integer.parseInt(requestMap.get("seance_id"));
                            List<SeanceParticipants> seanceParticipants = seanceParticipantsDao.findBySeanceId(seanceId);
                            for (SeanceParticipants sp : seanceParticipants) {
                                if ((sp.getUser().getRole() == UserFactory.UserRole.PROFESSOR) && !(sp.getUser().equals(user))) {
                                    return SystemUtils.getResponseEntity("A professor is already registered for this seance", HttpStatus.OK);
                                }
                            }
                            seanceParticipantsDao.save(getSeanceParticipantFromMap(requestMap));
                            return SystemUtils.getResponseEntity("Professor Participation Added Successfully", HttpStatus.OK);
                        }
                    }
                    return SystemUtils.getResponseEntity("Participant id does not exist", HttpStatus.OK);
                }

                else if(requestMap.containsKey("group_id")){
                    Integer seanceId = Integer.parseInt(requestMap.get("seance_id"));
                    Integer groupId = Integer.parseInt(requestMap.get("group_id"));

                    Optional<Group> optionalGroup = groupDao.findById(groupId);
                    if(optionalGroup.isPresent()){
                        Group group = optionalGroup.get();
                        List<User> students = userDao.findByGroupId(groupId);
                        for (User student : students) {
                            if(student.getRole() == UserFactory.UserRole.STUDENT){
                                SeanceParticipants seanceParticipant = new SeanceParticipants();
                                seanceParticipant.setSeance(seanceDao.getOne(seanceId));
                                seanceParticipant.setUser(student);
                                seanceParticipantsDao.save(seanceParticipant);
                            }
                        }

                        return SystemUtils.getResponseEntity("Participants Added Successfully", HttpStatus.OK);
                    }
                    else{
                        return SystemUtils.getResponseEntity("Group id does not exist", HttpStatus.OK);
                    }
                }

                }
            }else{
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS,HttpStatus.UNAUTHORIZED);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private SeanceParticipants getSeanceParticipantFromMap(Map<String, String> requestMap){
        Seance seance = new Seance();
        User user = new User();
        seance.setId(Integer.parseInt(requestMap.get("seance_id")));
        user.setId(Integer.parseInt(requestMap.get("participant_id")));
        SeanceParticipants seanceParticipants = new SeanceParticipants();
        seanceParticipants.setSeance(seance);
        seanceParticipants.setUser(user);
        return seanceParticipants;
    }


    @Override
    public ResponseEntity<List<SeanceParticipantWrapper>> getParticipants(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isAdmin() || jwtFilter.isProfessor()) {
                if (requestMap.containsKey("seance_id")) {
                    Integer seanceId = Integer.parseInt(requestMap.get("seance_id"));

                    if (jwtFilter.isProfessor()) {
                        User professor = seanceParticipantsDao.findBySeanceIdAndUserRole(seanceId, UserFactory.UserRole.PROFESSOR);
                        if (professor != null && !professor.getEmail().equals(jwtFilter.getCurrentUser())) {
                            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
                        }
                    }

                    Optional<Seance> optional = seanceDao.findById(seanceId);
                    if (optional.isEmpty()) {
                        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.NOT_FOUND);
                    }

                    Seance seance = optional.get();
                    LocalDate seanceDate = seance.getDate();
                    Time seanceTime = seance.getTime();
                    Time seanceDuration = seance.getDuration();
                    LocalDateTime seanceDebutDateTime = LocalDateTime.of(seanceDate, seanceTime.toLocalTime());
                    LocalDateTime seanceEndDateTime = seanceDebutDateTime.plusHours(seanceDuration.getHours()).plusMinutes(seanceDuration.getMinutes());

                    if(LocalDateTime.now().isBefore(seanceDebutDateTime)){
                        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.PRECONDITION_FAILED);
                    }

                    /*if(jwtFilter.isAdmin() && LocalDateTime.now().isBefore(seanceEndDateTime)){
                        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
                    }*/

                    List<SeanceParticipants> seanceParticipantsList = seanceParticipantsDao.findBySeanceId(seanceId);
                    List<SeanceParticipantWrapper> participants = new ArrayList<>();

                    // Check if seance is currently ongoing
                    if (LocalDateTime.now().isBefore(seanceEndDateTime)) {
                    }

                    for (SeanceParticipants seanceParticipant : seanceParticipantsList) {
                        User user = seanceParticipant.getUser();

                        /*if (jwtFilter.isProfessor() && user.getRole() != User.UserRole.STUDENT) {
                            continue;
                        }*/

                        PresenceRecord presenceRecord = checkPresence(seanceParticipant);
                        boolean isPresent = presenceRecord.isPresent();
                        boolean isValidate = presenceRecord.isValidate();
                        boolean isJustified = presenceRecord.isJustified();
                        Time entryTime = presenceRecord.entryTime();

                        if (requestMap.containsKey("status")) {
                            if (requestMap.get("status").equalsIgnoreCase("PRESENT") && !isPresent) {
                                continue;
                            }
                            if (requestMap.get("status").equalsIgnoreCase("ABSENT") && isPresent) {
                                continue;
                            }
                        }

                        SeanceParticipantWrapper participant = new SeanceParticipantWrapper(new UserWrapper(user), isPresent, isValidate, isJustified, entryTime);
                        participants.add(participant);
                    }

                    return new ResponseEntity<>(participants, HttpStatus.OK);
                }
            } else {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public PresenceRecord checkPresence(SeanceParticipants seanceParticipant) {
        Presence presence = presenceDao.findByUserAndSeance(seanceParticipant.getUser(), seanceParticipant.getSeance());
        boolean isPresent = false;
        boolean isValidate = false;
        boolean isJustified = false;
        Time entryTime = null;

        if (presence != null) {
            if (!seanceParticipant.isPresence() && !presence.isValidate()) {
                LocalDate seanceDate = seanceParticipant.getSeance().getDate();
                Time seanceTime = seanceParticipant.getSeance().getTime();
                Time seanceDuration = seanceParticipant.getSeance().getDuration();
                LocalDateTime seanceDateTime = LocalDateTime.of(seanceDate, seanceTime.toLocalTime());
                LocalDateTime endDateTime = seanceDateTime.plusHours(seanceDuration.getHours()).plusMinutes(seanceDuration.getMinutes());
                if (LocalDateTime.now().isAfter(endDateTime)) {
                    seanceParticipant.setPresence(true);
                    presence.setValidate(true);
                    presenceDao.save(presence);
                    seanceParticipantsDao.save(seanceParticipant);
                }
            }
            else if(seanceParticipant.isPresence() && presence.isValidate()) entryTime = presence.getEntrytime();
            isValidate = presence.isValidate();
            isPresent = seanceParticipant.isPresence();
        }

        if(!isPresent){
            Optional<Justification> optionalJustification = justificationDao.findByParticipantAndSeance(seanceParticipant.getUser(),seanceParticipant.getSeance());
            if(optionalJustification.isPresent()){
                Justification justification =optionalJustification.get();
                isJustified = justification.isAccepted();
            }
        }

        return new PresenceRecord(isPresent, isValidate, isJustified, entryTime);
    }

    @Override
    public ResponseEntity<String> validatePresence(Map<String, String> requestMap) {
        try {
            if(jwtFilter.isProfessor()){

                if(requestMap.containsKey("seance_id") && requestMap.containsKey("student_id") && requestMap.containsKey("is_presence")){
                    Optional<Seance> optionalSeance = seanceDao.findById(Integer.parseInt(requestMap.get("seance_id")));
                    if(optionalSeance.isPresent()){
                        Seance seance = optionalSeance.get();

                        User professor = seanceParticipantsDao.findBySeanceIdAndUserRole(seance.getId(), UserFactory.UserRole.PROFESSOR);
                        if (professor != null && !professor.getEmail().equals(jwtFilter.getCurrentUser())) {
                            return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS,HttpStatus.UNAUTHORIZED);
                        }


                        LocalDate seanceDate = seance.getDate();
                        Time seanceTime = seance.getTime();
                        Time seanceDuration = seance.getDuration();
                        LocalDateTime seanceDebutDateTime = LocalDateTime.of(seanceDate, seanceTime.toLocalTime());
                        LocalDateTime seanceEndDateTime = seanceDebutDateTime.plusHours(seanceDuration.getHours()).plusMinutes(seanceDuration.getMinutes());

                        if(LocalDateTime.now().isBefore(seanceDebutDateTime) || LocalDateTime.now().isAfter(seanceEndDateTime)){
                            return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS,HttpStatus.PRECONDITION_FAILED);
                        }

                        Optional<User> optionalStudent = userDao.findById(Integer.parseInt(requestMap.get("student_id")));
                        if(optionalStudent.isPresent()){

                            List<SeanceParticipants> seanceParticipantsList = seanceParticipantsDao.findBySeanceId(seance.getId());

                            boolean isParticipant = false;
                            for (SeanceParticipants seanceParticipant : seanceParticipantsList) {
                                if (seanceParticipant.getUser().getId().equals(optionalStudent.get().getId())) {
                                    isParticipant = true;
                                    break;
                                }
                            }

                            if (isParticipant) {
                                for (SeanceParticipants seanceParticipant : seanceParticipantsList) {
                                    if (seanceParticipant.getUser().getId().equals(optionalStudent.get().getId())) {
                                        Presence presence = presenceDao.findByUserAndSeance(seanceParticipant.getUser(), seanceParticipant.getSeance());
                                        seanceParticipant.setPresence(Boolean.valueOf(requestMap.get("is_presence")));
                                        presence.setValidate(true);
                                        seanceParticipantsDao.save(seanceParticipant);
                                        presenceDao.save(presence);
                                        break;
                                    }
                                }
                                return SystemUtils.getResponseEntity("Presence Validated Successfully", HttpStatus.OK);

                            } else {
                                return SystemUtils.getResponseEntity("Participant id does not exist", HttpStatus.OK);
                            }
                        }
                        return SystemUtils.getResponseEntity("Student id does not exist", HttpStatus.OK);
                    }
                    return SystemUtils.getResponseEntity("Seance id does not exist", HttpStatus.OK);
                }

            }else{
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS,HttpStatus.UNAUTHORIZED);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> justifyAbsence(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isStudent() || jwtFilter.isProfessor()) {
                if (requestMap.containsKey("seance_id") && (requestMap.containsKey("content") || requestMap.containsKey("support"))) {
                    Optional<Seance> optionalSeance = seanceDao.findById(Integer.parseInt(requestMap.get("seance_id")));
                    if (optionalSeance.isPresent()) {
                        Seance seance = optionalSeance.get();
                        User participant = userDao.findByEmailId(jwtFilter.getCurrentUser());

                        Optional<SeanceParticipants> participantOptional = seanceParticipantsDao.findBySeanceAndParticipant(seance,participant);
                        if(participantOptional.isPresent()){
                            SeanceParticipants seanceParticipant = participantOptional.get();
                            if(!seanceParticipant.isPresence()){

                                Optional<Justification> justificationOptional = justificationDao.findByParticipantAndSeance(participant, seance);

                                Justification justification;

                                if (justificationOptional.isPresent()) {
                                    justification = justificationOptional.get();
                                    if (requestMap.containsKey("content")) {
                                        justification.setContent(requestMap.get("content"));
                                    }
                                    if (requestMap.containsKey("support")) {
                                        String base64Content = requestMap.get("support");
                                        if (base64Content != null) {
                                            byte[] binaryContent = Base64.getDecoder().decode(base64Content);
                                            Blob supportBlob = new SerialBlob(binaryContent);
                                            justification.setSupport(supportBlob);
                                        } else {
                                            justification.setSupport(null);
                                        }
                                    }
                                } else {
                                    justification = new Justification();
                                    justification.setSeance(seance);
                                    justification.setUser(participant);
                                    justification.setContent(requestMap.get("content"));
                                    if (requestMap.containsKey("support")) {
                                        byte[] supportBytes = Base64.getDecoder().decode(requestMap.get("support"));
                                        Blob supportBlob = new SerialBlob(supportBytes);
                                        justification.setSupport(supportBlob);
                                    }
                                }
                                justificationDao.save(justification);
                                return SystemUtils.getResponseEntity("Absence Justified Successfully", HttpStatus.OK);

                            }
                            return SystemUtils.getResponseEntity("Absence not found", HttpStatus.OK);
                        }
                        return SystemUtils.getResponseEntity("Participation not found", HttpStatus.OK);
                    }
                    return SystemUtils.getResponseEntity("Seance does not exist", HttpStatus.OK);
                }
            } else {
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS, HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @Override
    public ResponseEntity<String> validateJustification(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isAdmin()) {
                if (requestMap.containsKey("seance_id") && requestMap.containsKey("participant_id") && requestMap.containsKey("is_accepted")) {
                    Optional<Seance> optionalSeance = seanceDao.findById(Integer.parseInt(requestMap.get("seance_id")));
                    if (optionalSeance.isPresent()) {
                        Seance seance = optionalSeance.get();
                        Optional<User> optionalUser = userDao.findById(Integer.valueOf(requestMap.get("participant_id")));
                        if(optionalUser.isPresent()){
                            User participant = optionalUser.get();
                            Optional<Justification> optionalJustification = justificationDao.findByParticipantAndSeance(participant,seance);
                            if(optionalJustification.isPresent()){
                                Justification justification = optionalJustification.get();
                                boolean isAccepted = Boolean.parseBoolean(requestMap.get("is_accepted"));
                                justification.setAccepted(isAccepted);
                                justificationDao.save(justification);
                                return SystemUtils.getResponseEntity("Justification Validated Successfully", HttpStatus.OK);
                            }
                            return SystemUtils.getResponseEntity("Justification does not exist", HttpStatus.OK);
                        }
                        return SystemUtils.getResponseEntity("Participant does not exist", HttpStatus.OK);
                    }
                    return SystemUtils.getResponseEntity("Seance does not exist", HttpStatus.OK);
                }
            } else {
                return SystemUtils.getResponseEntity(SystemCst.UNAUTHORIZED_ACCESS, HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return SystemUtils.getResponseEntity(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<Justification> getJustification(Map<String, String> requestMap) {
        try {
            if (jwtFilter.isAdmin()) {
                if (requestMap.containsKey("seance_id") && requestMap.containsKey("participant_id")) {
                    Optional<Seance> optionalSeance = seanceDao.findById(Integer.parseInt(requestMap.get("seance_id")));
                    if (optionalSeance.isPresent()) {
                        Seance seance = optionalSeance.get();
                        Optional<User> optionalUser = userDao.findById(Integer.valueOf(requestMap.get("participant_id")));
                        if(optionalUser.isPresent()){
                            User participant = optionalUser.get();
                            Optional<Justification> optionalJustification = justificationDao.findByParticipantAndSeance(participant,seance);
                            if(optionalJustification.isPresent()){
                                return new ResponseEntity<>(optionalJustification.get(),HttpStatus.OK);
                            }
                        }
                    }
                }
            } else {
                return new ResponseEntity<>(new Justification(),HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(new Justification(),HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<List<SeanceWrapper>> getSeances() {
        try {
            List<SeanceWrapper> listSeancedto = seanceDao.findAllSeances();

            return new ResponseEntity<>(listSeancedto, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(new ArrayList<>(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public ResponseEntity<String> deleteSeance(Integer seanceId) {
        try {
            seanceDao.deleteById(seanceId);

            return new ResponseEntity<>("seance deleted", HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(SystemCst.SOMETHING_WENT_WRONG, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
