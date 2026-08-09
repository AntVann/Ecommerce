{{- define "marketflow.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "marketflow.fullname" -}}
{{- printf "%s-%s" (include "marketflow.name" .) .Values.environment | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "marketflow.labels" -}}
app.kubernetes.io/part-of: marketflow
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
marketflow.io/environment: {{ .Values.environment | quote }}
{{- end -}}
{{- define "marketflow.image" -}}
{{- if hasPrefix "sha256:" .tag -}}
{{ .registry }}/{{ .name }}@{{ .tag }}
{{- else -}}
{{ .registry }}/{{ .name }}:{{ .tag }}
{{- end -}}
{{- end -}}
