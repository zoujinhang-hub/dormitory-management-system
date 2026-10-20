package edu.practice.housing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUsername(String username);
    List<Account> findByRoleOrderByIdAsc(Role role);
    long countByRole(Role role);
    long countByRoomId(Long roomId);
    boolean existsByRoomId(Long roomId);
}

interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByOrderByBuildingAscNumberAsc();
    boolean existsByBuildingAndNumberAndIdNot(String building, String number, Long id);
    boolean existsByBuildingAndNumber(String building, String number);
}

interface RepairRepository extends JpaRepository<Repair, Long> {
    List<Repair> findAllByOrderByCreatedAtDesc();
    List<Repair> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    boolean existsByStudentId(Long studentId);
    long countByStatusNot(RepairStatus status);
}

interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findAllByOrderByCreatedAtDesc();
}
