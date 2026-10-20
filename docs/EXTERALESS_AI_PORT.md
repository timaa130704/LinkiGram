# exteraless assistant port

Source: https://github.com/exteraless/exteraless
Revision: 1ad6a60a6834ffca722b1a22f4fba27ecb29a061
License: GPL-2.0 (same as LinkiGram; original packages retained).

The app.exteraless.ai package is imported from that revision. The settings base
uses LinkiGram native cells instead of Neko settings dependencies; the loading
indicator uses the existing RadialProgressView. English/Russian strings and
missing drawable resources are imported from upstream.

The previous automatic reply controller and update hooks are removed. The new
assistant is configured in LinkiGram General settings and invoked explicitly from
the message menu. Its answer can be inserted into the composer for review.
Existing auto-reply settings files are left on disk and are not imported into the
assistant configuration.

Relative last seen uses the existing localized minute/hour plurals, with the
absolute display retained after 24 hours, following exteraless. Phone hiding is
local and covers the settings subtitle, own profile phone row and drawer header.
