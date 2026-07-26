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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Legacy salt values from the original sites
    private static final String MAIN_SITE_SALT = "raksul_main_site_salt_2024";
    private static final String MA_SITE_SALT = "acquired_ma_site_salt_2024";

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

        if (!validatePassword(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // Upgrade legacy hash to BCrypt on successful login (lazy migration)
        if (!isBCrypt(user.getPasswordHash())) {
            upgradePasswordHash(user, request.getPassword());
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
        return validatePassword(password, userOpt.get().getPasswordHash());
    }

    /**
     * Validate password against hash - supports both BCrypt and legacy SHA-256
     */
    private boolean validatePassword(String password, String storedHash) {
        if (isBCrypt(storedHash)) {
            return passwordEncoder.matches(password, storedHash);
        }
        // Legacy hash - try plaintext, MD5, and SHA-256 with both salts
        return password.equals(storedHash) ||
               hashPasswordMD5(password).equals(storedHash) ||
               hashLegacyPassword(password, MAIN_SITE_SALT).equals(storedHash) ||
               hashLegacyPassword(password, MA_SITE_SALT).equals(storedHash);
    }

    /**
     * Check if the hash is BCrypt format
     */
    private boolean isBCrypt(String hash) {
        return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$"));
    }

    /**
     * Hash password using legacy SHA-256 + salt
     */
    private String hashLegacyPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Hash password using legacy MD5 (MA site format)
     */
    private String hashPasswordMD5(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Upgrade legacy hash to BCrypt (lazy migration on first login)
     */
    @Transactional
    private void upgradePasswordHash(User user, String plainPassword) {
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        userRepository.save(user);
        log.info("Upgraded password hash to BCrypt for user: {} (ID: {})", user.getEmail(), user.getId());
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
