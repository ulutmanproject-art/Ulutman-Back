package com.ulutman.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.ulutman.exception.IncorrectCodeException;
import com.ulutman.exception.MailSendingException;
import com.ulutman.exception.NotFoundException;
import com.ulutman.exception.PasswordsDoNotMatchException;
import com.ulutman.mapper.AuthMapper;
import com.ulutman.mapper.LoginMapper;
import com.ulutman.model.dto.*;
import com.ulutman.model.entities.Favorite;
import com.ulutman.model.entities.User;
import com.ulutman.model.entities.UserAccount;
import com.ulutman.model.enums.Role;
import com.ulutman.model.enums.Status;
import com.ulutman.repository.UserRepository;
import com.ulutman.security.jwt.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    AuthMapper authMapper;
    JwtUtil jwtUtil;
    PasswordEncoder passwordEncoder;
    AuthenticationManager manager;
    LoginMapper loginMapper;
    JavaMailSender mailSender;
    private final Resend resend;

    public AuthResponse saveUser(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }

        User user = authMapper.mapToEntity(request);
        user.setCreateDate(LocalDate.now());
        log.info("Пользователь успешно создан!");

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Пароли не совпадают");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setConfirmPassword(passwordEncoder.encode(request.getConfirmPassword()));

        user.setRole(request.getRole() != null ? request.getRole() : Role.USER);
        user.setStatus(Status.АКТИВНЫЙ);

        Set<Favorite> favorites = new HashSet<>();

        Favorite basket = new Favorite();
        favorites.add(basket);
        user.setFavorites(favorites);

        basket.setUser(user);

        UserAccount userAccount = new UserAccount();
        user.setUserAccount(userAccount);
        userAccount.setEmail(user.getUsername());

        userRepository.save(user);

        String jwt = jwtUtil.generateToken(user);

        return authMapper.mapToResponseWithToken(jwt, user);
    }

    public AuthResponse save(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        User user = authMapper.mapToEntity(request);
        user.setCreateDate(LocalDate.now());
        log.info("Пользователь успешно создан!");

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Пароли не совпадают");
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setConfirmPassword(passwordEncoder.encode(request.getConfirmPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.USER);
        user.setStatus(Status.АКТИВНЫЙ);

        Favorite basket = new Favorite();
        Set<Favorite> favorites = new HashSet<>();
        favorites.add(basket);

        user.setFavorites(favorites);

        UserAccount userAccount = new UserAccount();
        user.setUserAccount(userAccount);
        userAccount.setEmail(user.getUsername());

        userRepository.save(user);
        return authMapper.mapToResponse(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("Пользователь с такой почтой: " + request.getEmail() + " не найден !"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IncorrectCodeException("Введен неверный пароль!");
        }
        manager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        String jwt = jwtUtil.generateToken(user);
        return loginMapper.mapToResponse(jwt, user);
    }

    public AuthWithGoogleResponse registerUserWithGoogle(String token) {
        throw new UnsupportedOperationException("Google auth не поддерживается");
    }

//    public void sendPasswordResetCode(String email) throws EntityNotFoundException {
//        int pinCode = generatePinCode();
//        SimpleMailMessage message = new SimpleMailMessage();
//        message.setTo(email);
//        message.setFrom("ajzirektoktosunova853@gmail.com");
//        message.setSubject("Password reset");
//        message.setText(String.valueOf(pinCode));
//        try {
//            mailSender.send(message);
//            updateUserPinCode(email, pinCode);
//        } catch (MailException e) {
//            throw new MailSendingException("Не удалось отправить код для сброса пароля", e);
//        }
//    }


    public void sendPasswordResetCode(String email) throws EntityNotFoundException {

        int pinCode = generatePinCode();

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("Ulutman <noreply@ulutman-api.com>")
                .to(email)
                .subject("Восстановление пароля")
                .html("""
            <h2>Восстановление пароля</h2>

            <p>Здравствуйте!</p>

            <p>Для восстановления пароля на сайте <b>Ulutman</b> используйте следующий код:</p>

            <h1 style="letter-spacing: 5px;">%s</h1>

            <p>Введите этот код на странице восстановления пароля.</p>

            <hr>

            <p>С уважением,<br>
            Команда <b>Ulutman</b></p>
            """.formatted(pinCode))
                .build();
        try {

            resend.emails().send(params);

            updateUserPinCode(email, pinCode);

        } catch (ResendException e) {

            throw new MailSendingException(
                    "Не удалось отправить код для сброса пароля",
                    e
            );
        }
    }

    public ResponseEntity<String> resetPassword(String email, int pinCode, String newPassword, String confirmPassword)
            throws EntityNotFoundException, PasswordsDoNotMatchException {
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordsDoNotMatchException("Пароли не совпадают");
        } else {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

            if (pinCode == user.getPinCode()) {
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);
                return new ResponseEntity<>("Сброс пароля прошел успешно", HttpStatus.OK);
            }
        }
        return new ResponseEntity<>("Неверный PIN-код", HttpStatus.UNAUTHORIZED);
    }

    private int generatePinCode() {
        Random random = new Random();
        return random.nextInt(100000, 1000000);
    }

    private void updateUserPinCode(String email, int pinCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        user.setPinCode(pinCode);
        userRepository.save(user);
    }
}
