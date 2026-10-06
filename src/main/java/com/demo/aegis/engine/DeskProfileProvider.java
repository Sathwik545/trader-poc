package com.demo.aegis.engine;

import java.util.List;

/** Source of desk profiles. Demo: the desk_profile table. Later: the AEGIS desk table. */
public interface DeskProfileProvider {

    List<DeskProfile> loadAll();
}
