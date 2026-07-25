package com.raksul.idplatform.service;

import com.raksul.idplatform.model.AuthRequest;
import com.raksul.idplatform.model.AuthResponse;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getDisplayName() != null ? request.getDisplayName() : request.getEmail()
        );
        user.setSource(User.UserSource.INTERNAL);
        user = userRepository.save(user);

        log.info("New user registered: {} (ID: {})", user.getEmail(), user.getId());

        return createAuthResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

        if (userOpt.isEmpty()) {
            throw new RuntimeException("Invalid email or password");
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            throw new RuntimeException("Account has been deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        log.info("User logged in: {} (ID: {})", user.getEmail(), user.getId());

        return createAuthResponse(user);
    }

    public AuthResponse.UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new AuthResponse.UserInfo(user);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public AuthResponse.UserInfo updateUser(Long userId, String displayName, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (displayName != null) {
            user.setDisplayName(displayName);
        }
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(email);
        }

        user = userRepository.save(user);
        log.info("User updated: {} (ID: {})", user.getEmail(), user.getId());

        return new AuthResponse.UserInfo(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
        tokenService.revokeAllUserTokens(userId);
        log.info("User deactivated: {} (ID: {})", user.getEmail(), user.getId());
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public boolean validateCredentials(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return false;
        return passwordEncoder.matches(password, userOpt.get().getPasswordHash());
    }

    private AuthResponse createAuthResponse(User user) {
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                tokenService.getAccessTokenExpiry() / 1000,
                new AuthResponse.UserInfo(user)
        );
    }
}
