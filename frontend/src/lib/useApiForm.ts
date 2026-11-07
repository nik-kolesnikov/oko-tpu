// Маленький помощник для форм: значения + ошибки по полям из ответа VALIDATION_ERROR.
import { useState } from 'react';
import { ApiError } from '../api/client';

export function useApiForm<T extends Record<string, unknown>>(initial: T) {
  const [values, setValues] = useState<T>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const set = <K extends keyof T>(key: K, value: T[K]) => {
    setValues((v) => ({ ...v, [key]: value }));
    setErrors((e) => {
      if (!e[key as string]) return e;
      const next = { ...e };
      delete next[key as string];
      return next;
    });
  };

  /** Разложить ошибку сервера по полям. Возвращает true, если ошибка была про поля. */
  const applyServerError = (err: unknown): boolean => {
    if (err instanceof ApiError && err.details.length) {
      setErrors(err.fieldErrors());
      return true;
    }
    return false;
  };

  return { values, setValues, set, errors, setErrors, applyServerError };
}
