package com.foodordering.security;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.exception.AccountException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.UUID;

public final class SessionAuth {
    public static final String CURRENT_USER = "currentUser";
    private static final String AUTH_REVISION = SessionAuth.class.getName() + ".revision";
    private static final String REQUEST_SNAPSHOT = SessionAuth.class.getName() + ".snapshot";

    public record Snapshot(HttpSession session, String revision, ProfileResponse user) {
    }

    private SessionAuth() {
    }

    public static ProfileResponse requireUser(HttpServletRequest request) {
        return capture(request).user();
    }

    /** Keep the identity authorized by the filter for the remainder of this request. */
    public static Snapshot capture(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_SNAPSHOT) instanceof Snapshot bound) {
            requireCurrent(request, bound);
            return bound;
        }
        Snapshot snapshot = captureLogin(request);
        if (snapshot.session() == null || snapshot.user() == null) {
            throw unauthorized();
        }
        request.setAttribute(REQUEST_SNAPSHOT, snapshot);
        return snapshot;
    }

    /** Capture before password verification, including an existing anonymous session. */
    public static Snapshot captureLogin(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return new Snapshot(null, null, null);
            }
            synchronized (session) {
                Object value = session.getAttribute(CURRENT_USER);
                String revision = revision(session);
                return new Snapshot(session, revision, value instanceof ProfileResponse user ? user : null);
            }
        } catch (IllegalStateException exception) {
            throw unauthorized();
        }
    }

    public static void login(HttpServletRequest request, Snapshot before, ProfileResponse user, int timeout) {
        try {
            HttpSession session = before.session();
            if (session == null) {
                if (request.getSession(false) != null) {
                    throw unauthorized();
                }
                session = request.getSession(true);
            }
            synchronized (session) {
                if (before.session() != null) {
                    requireCurrent(request, before);
                    request.changeSessionId();
                }
                session.setMaxInactiveInterval(timeout);
                store(session, user);
            }
        } catch (IllegalStateException exception) {
            throw unauthorized();
        }
    }

    /** Refreshing profile data must not replace a login completed while the database call ran. */
    public static void refresh(HttpServletRequest request, Snapshot before, ProfileResponse user) {
        try {
            synchronized (before.session()) {
                requireCurrent(request, before);
                if (!before.user().id().equals(user.id()) || before.user().accountType() != user.accountType()) {
                    throw unauthorized();
                }
                storeFields(before.session(), user);
                request.setAttribute(REQUEST_SNAPSHOT, new Snapshot(before.session(), before.revision(), user));
            }
        } catch (IllegalStateException exception) {
            throw unauthorized();
        }
    }

    public static void invalidateIfCurrent(HttpServletRequest request, Snapshot before) {
        if (before == null || before.session() == null) {
            return;
        }
        synchronized (before.session()) {
            try {
                if (request.getSession(false) == before.session() && isCurrent(before)) {
                    before.session().invalidate();
                }
            } catch (IllegalStateException ignored) {
                // A concurrent logout already invalidated this session.
            }
        }
    }

    public static void logout(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                synchronized (session) {
                    session.invalidate();
                }
            }
        } catch (IllegalStateException exception) {
            // Logout remains idempotent if another request already invalidated the session.
        }
    }

    private static void requireCurrent(HttpServletRequest request, Snapshot before) {
        try {
            synchronized (before.session()) {
                if (request.getSession(false) != before.session() || !isCurrent(before)) {
                    throw unauthorized();
                }
            }
        } catch (IllegalStateException exception) {
            throw unauthorized();
        }
    }

    private static boolean isCurrent(Snapshot before) {
        Object value = before.session().getAttribute(CURRENT_USER);
        ProfileResponse user = value instanceof ProfileResponse profile ? profile : null;
        return Objects.equals(before.revision(), before.session().getAttribute(AUTH_REVISION))
                && (before.user() == null ? user == null : user != null
                && before.user().id().equals(user.id()) && before.user().accountType() == user.accountType());
    }

    private static String revision(HttpSession session) {
        if (session.getAttribute(AUTH_REVISION) instanceof String revision) {
            return revision;
        }
        String revision = UUID.randomUUID().toString();
        session.setAttribute(AUTH_REVISION, revision);
        return revision;
    }

    private static AccountException unauthorized() {
        return new AccountException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.", 401);
    }

    public static ProfileResponse requireCustomer(HttpServletRequest request) {
        ProfileResponse user = requireUser(request);
        if (user.accountType() != AccountType.CUSTOMER) {
            throw new AccountException("FORBIDDEN", "Chức năng này chỉ dành cho khách hàng.", 403);
        }
        return user;
    }

    public static void store(HttpSession session, ProfileResponse user) {
        try {
            synchronized (session) {
                session.setAttribute(AUTH_REVISION, UUID.randomUUID().toString());
                storeFields(session, user);
            }
        } catch (IllegalStateException exception) {
            throw unauthorized();
        }
    }

    private static void storeFields(HttpSession session, ProfileResponse user) {
        session.setAttribute(CURRENT_USER, user);
        session.setAttribute("userId", user.id());
        session.setAttribute("accountType", user.accountType().name());
        if (user.accountType() == AccountType.CUSTOMER) {
            session.setAttribute("customerId", user.id());
            session.removeAttribute("employeeId");
            session.removeAttribute("role");
        } else {
            session.setAttribute("employeeId", user.id());
            session.setAttribute("role", user.role() == null ? null : user.role().name());
            session.removeAttribute("customerId");
        }
    }
}
