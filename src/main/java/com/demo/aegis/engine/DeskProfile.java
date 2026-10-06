package com.demo.aegis.engine;

import java.util.Set;

/** An AEGIS desk / sub-desk and the products (family/group/type) it may trade. */
public record DeskProfile(String desk, String subDesk, String book, Set<String> allowedFgts, String deskHead,
                          String deskDelegate, String deskSupport) {
}
