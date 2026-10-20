package edu.practice.housing;

import jakarta.persistence.*;
import java.time.LocalDateTime;

enum Role { ADMIN, STUDENT }
enum RepairStatus { SUBMITTED, PROCESSING, COMPLETED }

@Entity
@Table(name = "accounts")
class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, unique = true)
    String username;
    @Column(nullable = false)
    String passwordHash;
    @Column(nullable = false)
    String displayName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Role role;
    Long roomId;

    protected Account() {}
    Account(String username, String passwordHash, String displayName, Role role, Long roomId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.roomId = roomId;
    }
}

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"building", "number"}))
class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String building;
    @Column(nullable = false)
    String number;
    @Column(nullable = false)
    int capacity;

    protected Room() {}
    Room(String building, String number, int capacity) {
        this.building = building;
        this.number = number;
        this.capacity = capacity;
    }
}

@Entity
@Table(name = "repairs")
class Repair {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    Long studentId;
    @Column(nullable = false)
    Long roomId;
    @Column(nullable = false, length = 500)
    String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    RepairStatus status = RepairStatus.SUBMITTED;
    @Column(nullable = false)
    LocalDateTime createdAt = LocalDateTime.now();

    protected Repair() {}
    Repair(Long studentId, Long roomId, String description) {
        this.studentId = studentId;
        this.roomId = roomId;
        this.description = description;
    }
}

@Entity
@Table(name = "notices")
class Notice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String title;
    @Column(nullable = false, length = 2000)
    String content;
    @Column(nullable = false)
    LocalDateTime createdAt = LocalDateTime.now();

    protected Notice() {}
    Notice(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
