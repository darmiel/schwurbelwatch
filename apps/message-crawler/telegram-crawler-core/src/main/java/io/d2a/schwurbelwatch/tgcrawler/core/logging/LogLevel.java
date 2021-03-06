package io.d2a.schwurbelwatch.tgcrawler.core.logging;

import lombok.Getter;

public enum LogLevel {

  DEBUG(1, AnsiColor.ANSI_WHITE),
  VALUE(3, AnsiColor.ANSI_PURPLE),
  INFO(5, AnsiColor.ANSI_CYAN),
  SUCCESS(6, AnsiColor.ANSI_GREEN),
  WARN(50, AnsiColor.ANSI_YELLOW),
  ERROR(100, AnsiColor.ANSI_RED_BACKGROUND);

  @Getter
  private final String consolePrefix;

  @Getter
  private final int level;

  LogLevel(final int level, final String consolePrefix) {
    this.consolePrefix = consolePrefix;
    this.level = level;
  }

  LogLevel(final int level) {
    this(level, null);
  }

}