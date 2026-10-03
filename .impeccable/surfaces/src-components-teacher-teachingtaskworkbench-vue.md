---
version: 1
slug: "src-components-teacher-teachingtaskworkbench-vue"
primary_target: "前端/src/components/teacher/TeachingTaskWorkbench.vue"
related_targets: ["前端/src/components/teacher/TeachingTaskList.vue","前端/src/components/teacher/TeachingTaskComposer.vue","前端/src/assets/styles/teaching-workspace.css"]
---

# Teacher task workspace

Archived: the user rejected this visual replacement and requested restoration of the preceding presentation. The new workspace components and stylesheet have been removed. Current live rendering uses TeachingPortal.vue and the incumbent teaching-live.css; retain this brief only as history, not visual authority.

Mode: Operate. Scope: authorized teacher task list, overdue assignment queue, task supervision, draft creation.

Teachers identify a task and its scope, find incomplete or failed student assignments, inspect reports, and arrange allowed follow-up. Success is an accurate chart-to-roster drilldown and a preserved return path.

Direction: extend the existing white/gray/emerald OfferPilot system with clear task identity, compact grouped filters, visible progress denominators and precise action scope. The first viewport identifies the task, dates, completion requirements and actionable counts. Task execution and capability goals are separate.

Evidence: real authorized assignments, per-person deadlines, exemption, attempt validity, raw answers and frozen score plan. No invented scores or implied comparability across plans. Unanswered reports do not support ability conclusions.

Constraints: desktop and 390px mobile; 44px controls; keyboard chart selection and readable counts; explicit full-class vs selected recipients; empty selected group must fail validation. Existing teaching permissions and publication flow remain authoritative.

Outstanding: wider overview/student/analytics redesign, large-dataset aggregation, chart accessibility testing with screen readers.
