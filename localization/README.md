# Translation workflow

The current interface is English. Dates and times follow the tablet locale; week start and time zone are household settings. RTL layout direction is supported by Android/Compose but translated RTL content is not yet available. Do not advertise the app as translated.

```sh
python tools/localize.py extract
python tools/localize.py audit
python tools/localize.py draft --locale de
python tools/localize.py compile --locale de
```

Drafting requires `FAMILYDAY_TRANSLATION_URL` and `FAMILYDAY_TRANSLATION_KEY` in the environment. The HTTPS service contract is documented in the script. Only static UI resources are sent, never private family messages or events. The workflow supports the nine priority locales from the specification, validates placeholder identity and line-break markers, extracts plurals, flags child/security/privacy strings and refuses to compile a draft until `human_reviewed` and `sensitive_reviewed` are both explicitly recorded as true.

Review meaning, child-facing tone, plural forms, date conventions and all placeholders. Languages needing additional plural quantities should add them to the workflow before publication; source-English quantities alone do not establish linguistic completeness. Compile produces Android resources. Then test font scaling, long text, focus traversal, TalkBack, portrait/landscape and RTL on a device. These layout reviews are not replaced by placeholder validation.

Store metadata lives separately in `store/`. Translate only verified shipping features. Pricing must come from the configured Amazon marketplace, not a translator or static resource. No translation API credentials are included in this repository.
