{{- define "name" -}}
{{ empty .Values.releaseName | ternary .Release.Name .Values.releaseName }}
{{- end -}}

{{- define "labels" }}
app: {{ empty .Values.releaseName | ternary .Release.Name .Values.releaseName }}
shortname: {{ .Values.shortname }}
team: {{ .Values.team }}
common: {{ .Chart.Version }}
environment: {{ .Values.env }}
app.kubernetes.io/instance: {{ empty .Values.releaseName | ternary .Release.Name .Values.releaseName }}
app.kubernetes.io/managed-by: Helm
{{- if .Values.labels }}
{{ toYaml .Values.labels }}
{{- end }}
{{- end }}


