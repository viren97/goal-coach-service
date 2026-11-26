{{- define "coach.fullname" -}}
{{- printf "%s" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "coach.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}

{{- define "coach.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "coach.secretName" -}}
{{- if .Values.externalSecret.enabled -}}
{{ include "coach.fullname" . }}-secrets
{{- else -}}
{{ required "set externalSecret.enabled=true or existingSecret" .Values.existingSecret }}
{{- end -}}
{{- end -}}
