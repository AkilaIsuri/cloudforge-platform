{{/* Labels shared by every resource in this chart */}}
{{- define "cloudforge.labels" -}}
app.kubernetes.io/part-of: cloudforge
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{/*
Full image reference for a component.
Usage: {{ include "cloudforge.image" (list $ $component) }}
*/}}
{{- define "cloudforge.image" -}}
{{- $root := index . 0 -}}
{{- $component := index . 1 -}}
{{- $tag := $component.tag | default $root.Values.global.imageTag -}}
{{- printf "%s/%s:%s" $root.Values.global.imageRepositoryPrefix $component.image (toString $tag) -}}
{{- end }}
