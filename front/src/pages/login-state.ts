export interface RegisterFormValues {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
  inviteCode: string;
}

export interface RegisterRequestPayload {
  username: string;
  email: string;
  phoneNumber: string;
  password: string;
  confirmPassword: string;
  inviteCode: string;
}

export function validateRegisterForm(values: RegisterFormValues) {
  if (values.password !== values.confirmPassword) {
    return '两次输入的密码不一致';
  }

  if (!values.inviteCode.trim()) {
    return '请输入邀请码';
  }

  return '';
}

export function buildRegisterPayload(values: RegisterFormValues): RegisterRequestPayload {
  return {
    username: values.username.trim(),
    email: values.email.trim(),
    phoneNumber: values.phoneNumber.trim(),
    password: values.password,
    confirmPassword: values.confirmPassword,
    inviteCode: values.inviteCode.trim(),
  };
}
