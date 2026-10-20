package edu.practice.housing;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
class DemoData {
    @Bean
    CommandLineRunner seedDemoData(AccountRepository accounts, RoomRepository rooms,
                                   RepairRepository repairs, NoticeRepository notices) {
        return args -> {
            if (accounts.count() != 0) return;
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            Room room = rooms.save(new Room("东区 1 栋", "101", 4));
            rooms.save(new Room("东区 1 栋", "102", 4));
            accounts.save(new Account("admin", encoder.encode("Admin123!"), "宿管老师", Role.ADMIN, null));
            Account student = accounts.save(new Account("2026001", encoder.encode("Student123!"),
                    "演示学生", Role.STUDENT, room.id));
            notices.save(new Notice("欢迎使用校园住宿服务平台", "这是演示数据。管理员可发布通知，学生可查看通知并提交报修。"));
            repairs.save(new Repair(student.id, room.id, "演示报修：桌灯无法正常开关"));
        };
    }
}
