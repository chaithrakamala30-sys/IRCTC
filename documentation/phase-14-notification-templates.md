# Phase 14: Notification Template Specifications

## 1. Template Structure
Templates reside in `notification_templates` with strict schema validation.
Each record specifies:
- `template_code`: Unique uppercase key
- `category`: Matches `NotificationCategory` enum
- `priority`: `LOW`, `NORMAL`, `HIGH`, `URGENT`
- `title_template`: Safe placeholder string
- `body_template`: Text with `{variable}` substitutions
- `default_deep_link`: Safe in-app route destination

## 2. Injection Prevention
Template variable interpolation is performed through safe token mapping, preventing script, HTML, or SQL injection.
