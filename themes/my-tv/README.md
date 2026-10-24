# my-tv

This fork's own theme for its TCL 75C745 (the TV in `examples/tcl-75c745-google-tv/`). It is
that folder's `config.json` plus the look: the dock, six columns, advanced effects, the top bar, the
Icelandic trivia and 5 wallpapers rotated hourly, chosen on the TV with the wallpaper preview from the 14 of
`themes/scenes/` and the 7 of `themes/iceland/` (moon lake, aurora, dunes, tea hills, autumn valley), dimmed 60%
and decoded in true colour (the TV has 2.4 GB).

```bash
TV_HOST=<tv ip> tools/theme.sh themes/my-tv
```

- The dock and tile order were arranged on the TV (Settings pill, hold OK: Reorder apps / Edit dock). That
  order is saved under the theme's `dock_apps`, so `dock_apps` stays as first pushed: changing it would make the
  next push discard the arrangement.
- The TV Settings tile is hidden; the Settings pill and the gear (quick panel) still open it.
- The launcher alone runs in Icelandic (greeting and date, launcher `res/values-is`; the TV stays English):
  `cmd locale set-app-locales com.gathin.tvhome --locales is`, set again after a fresh install.
- `trivia.txt` is checked by `tests/test_tcl_trivia.py`.
- The TV-specific keys (`hidden_apps` for TCL's system apps, `app_keys` for Netflix and YouTube) come from
  `examples/tcl-75c745-google-tv/config.json`; a change there belongs here too.
