package com.example.aipantry;

import com.example.aipantry.services.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class UnitsAndAliasTests {
  @Test
  void convertsBetweenCompatibleUnits() {
    Units u = new Units(Map.of(
      "g",  Map.of("to_g", 1.0),
      "kg", Map.of("to_g", 1000.0),
      "ml", Map.of("to_ml", 1.0)
    ));
    assertEquals(500.0, u.convert(0.5, "kg","g"), 1e-6);
    assertEquals(120.0, u.convert(120,"g","g"), 1e-6);        // identity
    assertEquals(2.0,   u.convert(2,"piece","g"), 1e-6);      // incompatible -> unchanged
  }

  @Test
  void aliasCanonicalizationWorks() {
    AliasResolver a = new AliasResolver(Map.of("bell pepper", List.of("red pepper","capsicum")));
    assertEquals("bell pepper", a.canonical("red pepper"));
    assertEquals("spinach", a.canonical("spinach"));
  }
}
