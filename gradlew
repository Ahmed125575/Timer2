#!/bin/sh
# StopTime GitHub launcher: uses the Gradle version installed by GitHub Actions.
# This avoids requiring a local Gradle installation on the GitHub runner.
exec gradle "$@"
