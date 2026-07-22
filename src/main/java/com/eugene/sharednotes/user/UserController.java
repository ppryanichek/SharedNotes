package com.eugene.sharednotes.user;

import com.eugene.sharednotes.user.dto.CreateUserDTO;
import com.eugene.sharednotes.user.dto.UserResponseDTO;
import com.eugene.sharednotes.user.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @GetMapping
    public Page<UserResponseDTO> findAll(Pageable pageable) {
        return userService.findAll(pageable);
    }

    @GetMapping("/id/{id}")
    public UserResponseDTO findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @GetMapping("/username/{username}")
    public UserResponseDTO findByUsername(@PathVariable String username) { return userService.findByUsername(username); }

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@RequestBody @Valid CreateUserDTO dto) {
        UserResponseDTO response = userService.createUser(dto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public UserResponseDTO updateUser(@PathVariable Long id,
                                      @RequestBody @Valid UserUpdateDTO dto) {
        return userService.updateUser(id, dto);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
