import { useEffect, useState } from 'react'
import { userApi } from '../api/userApi'
import { getApiErrorMessage } from '../api/apiClient'
import usePagedList from '../hooks/usePagedList'
import PageHeader from '../components/PageHeader'
import Pagination from '../components/Pagination'
import Badge from '../components/Badge'
import { ErrorState, LoadingState } from '../components/States'
const initial = {
  username: '',
  fullName: '',
  email: '',
  password: '',
  role: 'STAFF',
}
const roleLabels = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  ANALYST: 'Chuyên viên phân tích',
  STAFF: 'Nhân viên',
}
export default function UsersPage() {
  const list = usePagedList(userApi.getPage)
  const [roles, setRoles] = useState([]),
    [form, setForm] = useState(initial)
  const [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [busy, setBusy] = useState(false)
  const [rolesError, setRolesError] = useState('')
  function loadRoles() {
    setRolesError('')
    userApi
      .roles()
      .then(setRoles)
      .catch((e) => setRolesError(getApiErrorMessage(e)))
  }
  useEffect(() => {
    loadRoles()
  }, [])
  async function mutate(action, message) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(message)
      await list.load()
    } catch (e) {
      setError(getApiErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }
  function create(e) {
    e.preventDefault()
    mutate(async () => {
      await userApi.create({
        ...form,
        username: form.username.trim(),
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        roles: [form.role],
      })
      setForm(initial)
    }, 'Đã tạo tài khoản mới.')
  }
  const set = (name) => (e) =>
    setForm((previous) => ({ ...previous, [name]: e.target.value }))
  return (
    <>
      <PageHeader
        title="Người dùng & phân quyền"
        description="Cấp đúng quyền cho từng thành viên trong doanh nghiệp."
      />
      {error && (
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <div className="alert ai-notice" role="status">
          {notice}
        </div>
      )}
      <section className="panel form-panel" style={{ marginBottom: 22 }}>
        <h2>Thêm thành viên mới</h2>
        {rolesError ? (
          <ErrorState message={rolesError} onRetry={loadRoles} />
        ) : (
          <form onSubmit={create}>
            <fieldset disabled={busy || !roles.length}>
              <div className="form-grid">
                <label className="form-field">
                  <span className="form-label">Tên đăng nhập *</span>
                  <input
                    value={form.username}
                    onChange={set('username')}
                    required
                    maxLength={100}
                    autoComplete="off"
                  />
                </label>
                <label className="form-field">
                  <span className="form-label">Họ tên *</span>
                  <input
                    value={form.fullName}
                    onChange={set('fullName')}
                    required
                  />
                </label>
                <label className="form-field">
                  <span className="form-label">Email *</span>
                  <input
                    type="email"
                    value={form.email}
                    onChange={set('email')}
                    required
                  />
                </label>
                <label className="form-field">
                  <span className="form-label">Mật khẩu *</span>
                  <input
                    type="password"
                    value={form.password}
                    onChange={set('password')}
                    required
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    placeholder="Tối thiểu 8 ký tự"
                  />
                </label>
                <label className="form-field">
                  <span className="form-label">Vai trò ban đầu</span>
                  <select value={form.role} onChange={set('role')}>
                    {roles.map((role) => (
                      <option key={role.id} value={role.name}>
                        {roleLabels[role.name] || role.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <div className="form-actions">
                <button className="btn btn-primary">
                  {busy ? 'Đang lưu…' : 'Tạo tài khoản'}
                </button>
              </div>
            </fieldset>
          </form>
        )}
      </section>
      {list.loading ? (
        <LoadingState />
      ) : list.error ? (
        <ErrorState message={list.error} onRetry={list.load} />
      ) : (
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Thành viên trong doanh nghiệp</h2>
              <p>Thay đổi vai trò cần được xác nhận trước khi lưu.</p>
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Thành viên</th>
                  <th>Email</th>
                  <th>Quyền truy cập</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {list.content.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.fullName}</strong>
                      <br />
                      <small>{user.username}</small>
                    </td>
                    <td>{user.email}</td>
                    <td>
                      <RoleEditor
                        user={user}
                        roles={roles}
                        busy={busy || !roles.length}
                        onSave={(next) =>
                          mutate(
                            () => userApi.setRoles(user.id, next),
                            `Đã cập nhật quyền ${user.username}.`,
                          )
                        }
                      />
                    </td>
                    <td>
                      <Badge value={user.status} />
                    </td>
                    <td>
                      <button
                        className={`link-button ${user.status === 'ACTIVE' ? 'danger' : 'success'}`}
                        disabled={busy}
                        onClick={() => {
                          if (
                            window.confirm(
                              `${user.status === 'ACTIVE' ? 'Khóa' : 'Kích hoạt'} tài khoản ${user.username}?`,
                            )
                          )
                            mutate(
                              () =>
                                userApi.setStatus(
                                  user.id,
                                  user.status === 'ACTIVE'
                                    ? 'INACTIVE'
                                    : 'ACTIVE',
                                ),
                              'Đã cập nhật trạng thái tài khoản.',
                            )
                        }}
                      >
                        {user.status === 'ACTIVE' ? 'Khóa' : 'Kích hoạt'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination {...list} />
        </section>
      )}
    </>
  )
}
function RoleEditor({ user, roles, busy, onSave }) {
  const [selected, setSelected] = useState(user.roles || [])
  const dirty =
    [...selected].sort().join() !== [...(user.roles || [])].sort().join()
  return (
    <details className="role-editor">
      <summary>
        {user.roles?.map((role) => roleLabels[role] || role).join(', ') ||
          'Chọn vai trò'}
      </summary>
      <fieldset disabled={busy}>
        {roles.map((role) => (
          <label key={role.id}>
            <input
              type="checkbox"
              checked={selected.includes(role.name)}
              onChange={(e) =>
                setSelected((previous) =>
                  e.target.checked
                    ? [...previous, role.name]
                    : previous.filter((value) => value !== role.name),
                )
              }
            />
            {roleLabels[role.name] || role.name}
          </label>
        ))}
        <button
          type="button"
          className="btn btn-secondary"
          disabled={!dirty || !selected.length}
          onClick={() => onSave(selected)}
        >
          Lưu quyền
        </button>
      </fieldset>
    </details>
  )
}
