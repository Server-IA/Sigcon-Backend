package com.sigcon.backend.parametrization.users.interfaces;

import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.parametrization.users.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/getUsers")
    @PreAuthorize("hasAuthority('PERM_VIEW_USERS')")
    public ResponseEntity<?> getUsers(@RequestBody(required = false) UserDTO request, Pageable pageable) {
        return userService.getUsers(request, pageable);
    }

    @GetMapping
    public ResponseEntity<?> getUserInfo() {
        return userService.getUserInfo();
    }


    @PutMapping("/updateInfo")
    public ResponseEntity<?> updateInfo(@RequestBody UserDTO request){
        return userService.updateInfo(request);
    }

    @PutMapping("/updateUser/{userId}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_USER')")
    public ResponseEntity<?> updateUser(@PathVariable Long userId, @RequestBody UserDTO request){
        return userService.updateUser(userId, request);
    }

    @PostMapping("/deleteUser/{userId}")
    @PreAuthorize("hasAuthority('PERM_DELETE_USER')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        return userService.deleteUser(userId);
    }


}
