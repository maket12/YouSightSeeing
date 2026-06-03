package main

import (
    "fmt"
    "os"
    "path/filepath"
    "os/exec"

    "YouSightSeeing/backend/internal/app/usecase"
)

func main() {
    html, err := usecase.GenerateComparisonReport()
    if err != nil {
        fmt.Fprintf(os.Stderr, "failed to build report: %v\n", err)
        os.Exit(1)
    }

    out := "RECOMMENDATION_REPORT.html"
    // write to repo root
    cwd, _ := os.Getwd()
    path := filepath.Join(cwd, out)

    if err := os.WriteFile(path, []byte(html), 0644); err != nil {
        fmt.Fprintf(os.Stderr, "failed to write report: %v\n", err)
        os.Exit(1)
    }

    // try to open file on Windows using start
    cmd := exec.Command("cmd", "/c", "start", "", path)
    if err := cmd.Start(); err != nil {
        // fallback: print path
        fmt.Printf("Report written to %s\n", path)
        return
    }

    fmt.Printf("Report generated and opened: %s\n", path)
}
