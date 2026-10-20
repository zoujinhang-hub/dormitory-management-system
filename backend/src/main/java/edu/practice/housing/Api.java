package edu.practice.housing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

record LoginRequest(@NotBlank String username, @NotBlank String password) {}
record RoomRequest(@NotBlank String building, @NotBlank String number, @Min(1) @Max(12) int capacity) {}
record StudentRequest(@NotBlank String username, @NotBlank String displayName, String password, Long roomId) {}
record RepairRequest(@NotBlank @Size(max = 500) String description) {}
record NoticeRequest(@NotBlank String title, @NotBlank String content) {}
record StatusRequest(@NotNull RepairStatus status) {}
record CurrentUser(Long id, String username, String displayName, Role role, Long roomId) {}
record RoomView(Long id, String building, String number, int capacity, long occupied) {}
record StudentView(Long id, String username, String displayName, Long roomId) {}
record RepairView(Long id, Long studentId, String studentName, Long roomId, String roomName,
                  String description, RepairStatus status, java.time.LocalDateTime createdAt) {}
record NoticeView(Long id, String title, String content, java.time.LocalDateTime createdAt) {}
record Summary(long students, long rooms, long beds, long occupied, long openRepairs, long notices) {}

@Service
class HousingService {
    final AccountRepository accounts;
    final RoomRepository rooms;
    final RepairRepository repairs;
    final NoticeRepository notices;
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    HousingService(AccountRepository accounts, RoomRepository rooms, RepairRepository repairs, NoticeRepository notices) {
        this.accounts = accounts;
        this.rooms = rooms;
        this.repairs = repairs;
        this.notices = notices;
    }

    Account requireUser(HttpSession session) {
        Object id = session.getAttribute("accountId");
        if (!(id instanceof Long)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return accounts.findById((Long) id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
    }

    Account requireAdmin(HttpSession session) {
        Account user = requireUser(session);
        if (user.role != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        return user;
    }

    Room room(Long id) {
        return rooms.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "宿舍不存在"));
    }

    void checkCapacity(Long roomId, Long currentRoomId) {
        if (roomId == null || roomId.equals(currentRoomId)) return;
        Room room = room(roomId);
        if (accounts.countByRoomId(roomId) >= room.capacity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该宿舍已满");
        }
    }

    RoomView roomView(Room room) {
        return new RoomView(room.id, room.building, room.number, room.capacity, accounts.countByRoomId(room.id));
    }

    StudentView studentView(Account student) {
        return new StudentView(student.id, student.username, student.displayName, student.roomId);
    }

    RepairView repairView(Repair repair) {
        String studentName = accounts.findById(repair.studentId).map(a -> a.displayName).orElse("已删除学生");
        String roomName = rooms.findById(repair.roomId).map(r -> r.building + " · " + r.number).orElse("已删除宿舍");
        return new RepairView(repair.id, repair.studentId, studentName, repair.roomId, roomName,
                repair.description, repair.status, repair.createdAt);
    }

    NoticeView noticeView(Notice notice) {
        return new NoticeView(notice.id, notice.title, notice.content, notice.createdAt);
    }
}

@RestController
@RequestMapping("/api")
class HousingController {
    private final HousingService service;

    HousingController(HousingService service) {
        this.service = service;
    }

    @PostMapping("/login")
    CurrentUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Account user = service.accounts.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));
        if (!service.passwords.matches(request.password(), user.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        HttpSession existing = servletRequest.getSession(false);
        if (existing != null) existing.invalidate();
        servletRequest.getSession(true).setAttribute("accountId", user.id);
        return currentUser(user);
    }

    @PostMapping("/logout")
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @GetMapping("/me")
    CurrentUser me(HttpSession session) { return currentUser(service.requireUser(session)); }

    private CurrentUser currentUser(Account user) {
        return new CurrentUser(user.id, user.username, user.displayName, user.role, user.roomId);
    }

    @GetMapping("/summary")
    Summary summary(HttpSession session) {
        service.requireAdmin(session);
        long beds = service.rooms.findAll().stream().mapToLong(r -> r.capacity).sum();
        return new Summary(service.accounts.countByRole(Role.STUDENT), service.rooms.count(), beds,
                service.accounts.countByRole(Role.STUDENT) - service.accounts.findByRoleOrderByIdAsc(Role.STUDENT).stream().filter(a -> a.roomId == null).count(),
                service.repairs.countByStatusNot(RepairStatus.COMPLETED), service.notices.count());
    }

    @GetMapping("/rooms")
    List<RoomView> rooms(HttpSession session) {
        Account user = service.requireUser(session);
        return service.rooms.findAllByOrderByBuildingAscNumberAsc().stream()
                .filter(room -> user.role == Role.ADMIN || room.id.equals(user.roomId))
                .map(service::roomView).toList();
    }

    @PostMapping("/rooms")
    @Transactional
    RoomView createRoom(@Valid @RequestBody RoomRequest request, HttpSession session) {
        service.requireAdmin(session);
        if (service.rooms.existsByBuildingAndNumber(request.building(), request.number()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "宿舍已存在");
        return service.roomView(service.rooms.save(new Room(request.building(), request.number(), request.capacity())));
    }

    @PutMapping("/rooms/{id}")
    @Transactional
    RoomView updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest request, HttpSession session) {
        service.requireAdmin(session);
        Room room = service.room(id);
        if (service.rooms.existsByBuildingAndNumberAndIdNot(request.building(), request.number(), id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "宿舍已存在");
        if (request.capacity() < service.accounts.countByRoomId(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "床位数不能小于已入住人数");
        room.building = request.building();
        room.number = request.number();
        room.capacity = request.capacity();
        return service.roomView(room);
    }

    @DeleteMapping("/rooms/{id}")
    @Transactional
    void deleteRoom(@PathVariable Long id, HttpSession session) {
        service.requireAdmin(session);
        Room room = service.room(id);
        if (service.accounts.existsByRoomId(id) || service.repairs.findAll().stream().anyMatch(r -> r.roomId.equals(id)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "宿舍有入住或报修记录，不能删除");
        service.rooms.delete(room);
    }

    @GetMapping("/students")
    List<StudentView> students(HttpSession session) {
        service.requireAdmin(session);
        return service.accounts.findByRoleOrderByIdAsc(Role.STUDENT).stream().map(service::studentView).toList();
    }

    @PostMapping("/students")
    @Transactional
    StudentView createStudent(@Valid @RequestBody StudentRequest request, HttpSession session) {
        service.requireAdmin(session);
        if (request.password() == null || request.password().length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "初始密码至少 8 位");
        if (service.accounts.findByUsername(request.username()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "学号已存在");
        service.checkCapacity(request.roomId(), null);
        Account student = new Account(request.username(), service.passwords.encode(request.password()),
                request.displayName(), Role.STUDENT, request.roomId());
        return service.studentView(service.accounts.save(student));
    }

    @PutMapping("/students/{id}")
    @Transactional
    StudentView updateStudent(@PathVariable Long id, @Valid @RequestBody StudentRequest request, HttpSession session) {
        service.requireAdmin(session);
        Account student = service.accounts.findById(id).filter(a -> a.role == Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学生不存在"));
        if (!student.username.equals(request.username()) && service.accounts.findByUsername(request.username()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "学号已存在");
        service.checkCapacity(request.roomId(), student.roomId);
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 8 位");
            student.passwordHash = service.passwords.encode(request.password());
        }
        student.username = request.username();
        student.displayName = request.displayName();
        student.roomId = request.roomId();
        return service.studentView(student);
    }

    @DeleteMapping("/students/{id}")
    @Transactional
    void deleteStudent(@PathVariable Long id, HttpSession session) {
        service.requireAdmin(session);
        Account student = service.accounts.findById(id).filter(a -> a.role == Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学生不存在"));
        if (service.repairs.existsByStudentId(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "学生有报修记录，不能删除");
        service.accounts.delete(student);
    }

    @GetMapping("/repairs")
    List<RepairView> repairs(HttpSession session) {
        Account user = service.requireUser(session);
        List<Repair> items = user.role == Role.ADMIN ? service.repairs.findAllByOrderByCreatedAtDesc()
                : service.repairs.findByStudentIdOrderByCreatedAtDesc(user.id);
        return items.stream().map(service::repairView).toList();
    }

    @PostMapping("/repairs")
    @Transactional
    RepairView createRepair(@Valid @RequestBody RepairRequest request, HttpSession session) {
        Account user = service.requireUser(session);
        if (user.role != Role.STUDENT) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅学生可提交报修");
        if (user.roomId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先分配宿舍");
        return service.repairView(service.repairs.save(new Repair(user.id, user.roomId, request.description())));
    }

    @PatchMapping("/repairs/{id}/status")
    @Transactional
    RepairView updateRepair(@PathVariable Long id, @Valid @RequestBody StatusRequest request, HttpSession session) {
        service.requireAdmin(session);
        Repair repair = service.repairs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "报修单不存在"));
        if (request.status().ordinal() < repair.status.ordinal())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "报修状态不能回退");
        repair.status = request.status();
        return service.repairView(repair);
    }

    @GetMapping("/notices")
    List<NoticeView> notices(HttpSession session) {
        service.requireUser(session);
        return service.notices.findAllByOrderByCreatedAtDesc().stream().map(service::noticeView).toList();
    }

    @PostMapping("/notices")
    @Transactional
    NoticeView createNotice(@Valid @RequestBody NoticeRequest request, HttpSession session) {
        service.requireAdmin(session);
        return service.noticeView(service.notices.save(new Notice(request.title(), request.content())));
    }

    @PutMapping("/notices/{id}")
    @Transactional
    NoticeView updateNotice(@PathVariable Long id, @Valid @RequestBody NoticeRequest request, HttpSession session) {
        service.requireAdmin(session);
        Notice notice = service.notices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        notice.title = request.title();
        notice.content = request.content();
        return service.noticeView(notice);
    }

    @DeleteMapping("/notices/{id}")
    @Transactional
    void deleteNotice(@PathVariable Long id, HttpSession session) {
        service.requireAdmin(session);
        Notice notice = service.notices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        service.notices.delete(notice);
    }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalid() { return Map.of("message", "请检查必填项和输入范围"); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict() { return Map.of("message", "数据与已有记录冲突"); }
}
